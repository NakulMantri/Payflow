package com.payflow.service;

import com.payflow.dto.PaymentResponse;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.Role;
import com.payflow.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRetryWorkerTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private MockPaymentGatewayService gatewayService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private NotificationProducerService notificationProducer;

    @Mock
    private IdempotencyService idempotencyService;

    private PaymentRetryWorker retryWorker;

    private PaymentTransaction testTxn;
    private User testUser;
    private Biller testBiller;

    @BeforeEach
    void setUp() {
        retryWorker = new PaymentRetryWorker(
                transactionRepository,
                gatewayService,
                paymentService,
                notificationProducer,
                idempotencyService,
                3,
                2000,
                2.0
        );

        testUser = new User("retry@payflow.com", "hash", "Retry User", "9999999999", Role.ROLE_USER);
        testUser.setId(3L);

        testBiller = new Biller("AIRTEL", "Airtel", BillerCategory.MOBILE_PREPAID, ".*", "Number", BigDecimal.ZERO, "ESC_AIRTEL");

        testTxn = new PaymentTransaction();
        testTxn.setId("txn-retry-1");
        testTxn.setTransactionRef("TXN_RETRY_001");
        testTxn.setIdempotencyKey("idemp_retry_1");
        testTxn.setUser(testUser);
        testTxn.setBiller(testBiller);
        testTxn.setConsumerNumber("9999999999");
        testTxn.setAmount(new BigDecimal("199.00"));
        testTxn.setStatus(PaymentStatus.RETRYING);
        testTxn.setRetryCount(1);
    }

    @Test
    @DisplayName("Should detect payment that settled on gateway and finalize success")
    void testProcessRetryQueue_settledOnGateway() {
        when(transactionRepository.findPendingRetries(eq(PaymentStatus.RETRYING), any()))
                .thenReturn(List.of(testTxn));

        MockPaymentGatewayService.GatewayTransactionRecord settledRecord =
                new MockPaymentGatewayService.GatewayTransactionRecord(
                        testTxn.getTransactionRef(),
                        "GW_SETTLED_888",
                        testTxn.getAmount(),
                        testBiller.getCode(),
                        testTxn.getConsumerNumber(),
                        PaymentStatus.SUCCESS
                );
        when(gatewayService.queryGatewayStatus(testTxn.getTransactionRef()))
                .thenReturn(Optional.of(settledRecord));

        when(paymentService.finalizeSuccess(eq("txn-retry-1"), eq("GW_SETTLED_888"), eq("00")))
                .thenReturn(testTxn);
        when(paymentService.toDto(any())).thenReturn(new PaymentResponse());

        retryWorker.processRetryQueue();

        verify(paymentService).finalizeSuccess("txn-retry-1", "GW_SETTLED_888", "00");
        verify(idempotencyService).recordSuccess(eq("idemp_retry_1"), eq(3L), any());
        verify(notificationProducer).publishPaymentNotification(any());
    }

    @Test
    @DisplayName("Should advance exponential backoff when retry attempts have not reached maximum")
    void testProcessRetryQueue_exponentialBackoff() {
        when(transactionRepository.findPendingRetries(eq(PaymentStatus.RETRYING), any()))
                .thenReturn(List.of(testTxn));
        when(gatewayService.queryGatewayStatus(testTxn.getTransactionRef()))
                .thenReturn(Optional.empty());

        when(gatewayService.processPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new MockPaymentGatewayService.GatewayResult(
                        MockPaymentGatewayService.GatewayOutcome.TIMEOUT,
                        null,
                        "91",
                        "Timeout again"
                ));

        retryWorker.processRetryQueue();

        // Should schedule retry attempt 2 with exponential backoff
        verify(paymentService).scheduleRetry(eq("txn-retry-1"), eq(2), any(LocalDateTime.class), anyString());
        verify(paymentService, never()).rollbackAndFail(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Should trigger automatic rollback & refund when max retries are exhausted")
    void testProcessRetryQueue_maxRetriesExhausted() {
        testTxn.setRetryCount(2); // this attempt will be attempt 3 = maxRetries

        when(transactionRepository.findPendingRetries(eq(PaymentStatus.RETRYING), any()))
                .thenReturn(List.of(testTxn));
        when(gatewayService.queryGatewayStatus(testTxn.getTransactionRef()))
                .thenReturn(Optional.empty());

        when(gatewayService.processPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new MockPaymentGatewayService.GatewayResult(
                        MockPaymentGatewayService.GatewayOutcome.TIMEOUT,
                        "GW_FAIL",
                        "91",
                        "Permanent Timeout"
                ));
        when(paymentService.rollbackAndFail(eq("txn-retry-1"), anyString(), eq("GW_FAIL"), eq("91")))
                .thenReturn(testTxn);
        when(paymentService.toDto(any())).thenReturn(new PaymentResponse());

        retryWorker.processRetryQueue();

        verify(paymentService).rollbackAndFail(eq("txn-retry-1"), anyString(), eq("GW_FAIL"), eq("91"));
        verify(notificationProducer).publishPaymentNotification(any());
    }
}
