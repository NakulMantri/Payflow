package com.payflow.service;

import com.payflow.dto.PaymentInitiateRequest;
import com.payflow.dto.PaymentNotificationEvent;
import com.payflow.dto.PaymentResponse;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.Role;
import com.payflow.exception.IdempotencyConflictException;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.repository.PaymentTransactionRepository;
import com.payflow.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private BillerService billerService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private MockPaymentGatewayService gatewayService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NotificationProducerService notificationProducer;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private PaymentTransactionHelper txHelper;

    private PaymentService paymentService;

    private User testUser;
    private Wallet testWallet;
    private Biller testBiller;
    private PaymentTransaction sampleTxn;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                transactionRepository,
                walletRepository,
                billerService,
                ledgerService,
                gatewayService,
                idempotencyService,
                notificationProducer,
                rateLimiterService,
                txHelper,
                3,
                2000
        );

        testUser = new User("alex@example.com", "hash", "Alex Morgan", "9876543210", Role.ROLE_USER);
        testUser.setId(1L);

        testWallet = new Wallet(testUser, "INR", new BigDecimal("1000.0000"));
        testWallet.setId(10L);

        testBiller = new Biller(
                "TATA_POWER",
                "Tata Power",
                BillerCategory.ELECTRICITY,
                "^[0-9]{10}$",
                "Consumer Number",
                BigDecimal.ZERO,
                "ESCROW_TATA"
        );
        testBiller.setId(100L);

        sampleTxn = new PaymentTransaction();
        sampleTxn.setId("txn-123");
        sampleTxn.setTransactionRef("TXN_123");
        sampleTxn.setIdempotencyKey("idemp_key_1");
        sampleTxn.setUser(testUser);
        sampleTxn.setWallet(testWallet);
        sampleTxn.setBiller(testBiller);
        sampleTxn.setConsumerNumber("1234567890");
        sampleTxn.setAmount(new BigDecimal("250.00"));
        sampleTxn.setCurrency("INR");
        sampleTxn.setStatus(PaymentStatus.PENDING_GATEWAY);
    }

    @Test
    @DisplayName("Should return cached response on idempotency HIT without double-charging")
    void testPaymentIdempotencyHit_returnsCached() {
        String idempKey = "idemp_test_123";
        PaymentInitiateRequest request = new PaymentInitiateRequest(100L, "1234567890", new BigDecimal("500.00"), false, null);

        PaymentResponse cachedResponse = new PaymentResponse();
        cachedResponse.setId("txn-uuid-1");
        cachedResponse.setTransactionRef("TXN_CACHED_01");
        cachedResponse.setStatus(PaymentStatus.SUCCESS);
        cachedResponse.setAmount(new BigDecimal("500.00"));

        when(idempotencyService.acquireOrCheck(eq(idempKey), eq(1L), any()))
                .thenReturn(new IdempotencyService.IdempotencyCheckResult(true, cachedResponse));

        PaymentResponse actual = paymentService.processPayment(testUser, idempKey, request);

        assertThat(actual).isNotNull();
        assertThat(actual.getTransactionRef()).isEqualTo("TXN_CACHED_01");
        assertThat(actual.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // Verify no wallet debit or gateway invocation happened!
        verify(txHelper, never()).prepareAndLockPayment(any(), any(), any(), any(), anyInt());
        verify(gatewayService, never()).processPayment(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException if transaction is already in-flight")
    void testPaymentIdempotencyInFlight_throwsConflict() {
        String idempKey = "idemp_test_conflict";
        PaymentInitiateRequest request = new PaymentInitiateRequest(100L, "1234567890", new BigDecimal("200.00"), false, null);

        when(idempotencyService.acquireOrCheck(eq(idempKey), eq(1L), any()))
                .thenThrow(new IdempotencyConflictException("Transaction currently in progress"));

        assertThatThrownBy(() -> paymentService.processPayment(testUser, idempKey, request))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Transaction currently in progress");

        verify(txHelper, never()).prepareAndLockPayment(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException and release idempotency lock when balance is too low")
    void testInsufficientBalance_throwsException_andReleasesLock() {
        String idempKey = "idemp_test_low_bal";
        PaymentInitiateRequest request = new PaymentInitiateRequest(100L, "1234567890", new BigDecimal("5000.00"), false, null);

        when(idempotencyService.acquireOrCheck(eq(idempKey), eq(1L), any()))
                .thenReturn(new IdempotencyService.IdempotencyCheckResult(false, null));
        when(billerService.getBillerEntity(100L)).thenReturn(testBiller);
        when(txHelper.prepareAndLockPayment(eq(testUser), eq(testBiller), eq(idempKey), eq(request), anyInt()))
                .thenThrow(new InsufficientBalanceException("Insufficient wallet balance"));

        assertThatThrownBy(() -> paymentService.processPayment(testUser, idempKey, request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient wallet balance");

        // Verify idempotency lock was released so user can retry after topping up
        verify(idempotencyService).releaseLock(idempKey);
        verify(gatewayService, never()).processPayment(anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should process payment successfully, record double entry, and emit Kafka event")
    void testPaymentSuccess_completesLedger_andPublishesKafka() {
        String idempKey = "idemp_success_01";
        PaymentInitiateRequest request = new PaymentInitiateRequest(100L, "1234567890", new BigDecimal("250.00"), false, null);

        when(idempotencyService.acquireOrCheck(eq(idempKey), eq(1L), any()))
                .thenReturn(new IdempotencyService.IdempotencyCheckResult(false, null));
        when(billerService.getBillerEntity(100L)).thenReturn(testBiller);

        when(txHelper.prepareAndLockPayment(eq(testUser), eq(testBiller), eq(idempKey), eq(request), anyInt()))
                .thenReturn(sampleTxn);

        when(gatewayService.processPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new MockPaymentGatewayService.GatewayResult(
                        MockPaymentGatewayService.GatewayOutcome.SUCCESS,
                        "GW_REF_999",
                        "00",
                        "Approved"
                ));

        PaymentTransaction successTxn = new PaymentTransaction();
        successTxn.setId("txn-123");
        successTxn.setTransactionRef("TXN_123");
        successTxn.setIdempotencyKey(idempKey);
        successTxn.setUser(testUser);
        successTxn.setWallet(testWallet);
        successTxn.setBiller(testBiller);
        successTxn.setConsumerNumber("1234567890");
        successTxn.setAmount(new BigDecimal("250.00"));
        successTxn.setCurrency("INR");
        successTxn.markSuccess("GW_REF_999", "00");

        when(txHelper.finalizeSuccess("txn-123", "GW_REF_999", "00")).thenReturn(successTxn);

        PaymentResponse response = paymentService.processPayment(testUser, idempKey, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getGatewayReference()).isEqualTo("GW_REF_999");

        // Verify idempotency cache updated
        verify(idempotencyService).recordSuccess(eq(idempKey), eq(1L), any(PaymentResponse.class));

        // Verify Kafka notification dispatched
        verify(notificationProducer).publishPaymentNotification(any(PaymentNotificationEvent.class));
    }

    @Test
    @DisplayName("Should handle transient gateway timeout by scheduling transaction for retry queue")
    void testPaymentTimeout_schedulesRetry() {
        String idempKey = "idemp_timeout_01";
        PaymentInitiateRequest request = new PaymentInitiateRequest(100L, "1234567890", new BigDecimal("300.00"), false, null);

        when(idempotencyService.acquireOrCheck(eq(idempKey), eq(1L), any()))
                .thenReturn(new IdempotencyService.IdempotencyCheckResult(false, null));
        when(billerService.getBillerEntity(100L)).thenReturn(testBiller);

        when(txHelper.prepareAndLockPayment(eq(testUser), eq(testBiller), eq(idempKey), eq(request), anyInt()))
                .thenReturn(sampleTxn);

        when(gatewayService.processPayment(anyString(), any(), anyString(), anyString()))
                .thenReturn(new MockPaymentGatewayService.GatewayResult(
                        MockPaymentGatewayService.GatewayOutcome.TIMEOUT,
                        null,
                        "91",
                        "Gateway timeout"
                ));

        PaymentTransaction retryTxn = new PaymentTransaction();
        retryTxn.setId("txn-123");
        retryTxn.setTransactionRef("TXN_RETRY_123");
        retryTxn.setIdempotencyKey(idempKey);
        retryTxn.setUser(testUser);
        retryTxn.setWallet(testWallet);
        retryTxn.setBiller(testBiller);
        retryTxn.setConsumerNumber("1234567890");
        retryTxn.setAmount(new BigDecimal("300.00"));
        retryTxn.setCurrency("INR");
        retryTxn.setStatus(PaymentStatus.RETRYING);
        retryTxn.setRetryCount(1);

        when(txHelper.scheduleRetry(eq("txn-123"), eq(1), any(LocalDateTime.class), anyString()))
                .thenReturn(retryTxn);

        PaymentResponse response = paymentService.processPayment(testUser, idempKey, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.RETRYING);
        assertThat(response.getRetryCount()).isEqualTo(1);
    }
}
