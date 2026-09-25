package com.payflow.service;

import com.payflow.dto.PaymentResponse;
import com.payflow.dto.ReconciliationSummaryDto;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.ReconciliationRecord;
import com.payflow.entity.User;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.Role;
import com.payflow.repository.PaymentTransactionRepository;
import com.payflow.repository.ReconciliationRecordRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private ReconciliationRecordRepository reconciliationRepository;

    @Mock
    private MockPaymentGatewayService gatewayService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private IdempotencyService idempotencyService;

    private ReconciliationService reconciliationService;

    private PaymentTransaction pendingTxn;
    private User testUser;
    private Biller testBiller;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(
                transactionRepository,
                reconciliationRepository,
                gatewayService,
                paymentService,
                idempotencyService
        );

        testUser = new User("recon@payflow.com", "hash", "Recon User", "9999999999", Role.ROLE_USER);
        testUser.setId(4L);

        testBiller = new Biller("DTH_DISHTV", "Dish TV", BillerCategory.DTH, ".*", "ID", BigDecimal.ZERO, "ESC_DISH");

        pendingTxn = new PaymentTransaction();
        pendingTxn.setId("txn-recon-1");
        pendingTxn.setTransactionRef("TXN_RECON_001");
        pendingTxn.setIdempotencyKey("idemp_recon_1");
        pendingTxn.setUser(testUser);
        pendingTxn.setBiller(testBiller);
        pendingTxn.setAmount(new BigDecimal("499.00"));
        pendingTxn.setStatus(PaymentStatus.PENDING_GATEWAY);
        pendingTxn.setCreatedAt(LocalDateTime.now().minusSeconds(10));
    }

    @Test
    @DisplayName("Should detect gateway SUCCESS and auto-reconcile pending internal transaction")
    void testReconciliation_gatewaySuccess_autoResolves() {
        when(transactionRepository.findTransactionsForReconciliation(any(), any()))
                .thenReturn(List.of(pendingTxn));

        MockPaymentGatewayService.GatewayTransactionRecord gwRecord =
                new MockPaymentGatewayService.GatewayTransactionRecord(
                        pendingTxn.getTransactionRef(),
                        "GW_RECON_777",
                        new BigDecimal("499.00"),
                        testBiller.getCode(),
                        "12345",
                        PaymentStatus.SUCCESS
                );
        when(gatewayService.queryGatewayStatus(pendingTxn.getTransactionRef()))
                .thenReturn(Optional.of(gwRecord));

        when(paymentService.finalizeSuccess(eq("txn-recon-1"), eq("GW_RECON_777"), eq("00")))
                .thenReturn(pendingTxn);
        when(paymentService.toDto(any())).thenReturn(new PaymentResponse());
        when(reconciliationRepository.save(any(ReconciliationRecord.class))).thenAnswer(i -> {
            ReconciliationRecord r = i.getArgument(0);
            r.setId(101L);
            return r;
        });

        ReconciliationSummaryDto summary = reconciliationService.runReconciliation();

        assertThat(summary.getTotalChecked()).isEqualTo(1);
        assertThat(summary.getDiscrepancyCount()).isEqualTo(1);
        assertThat(summary.getAutoResolvedCount()).isEqualTo(1);

        verify(paymentService).finalizeSuccess("txn-recon-1", "GW_RECON_777", "00");
        verify(reconciliationRepository).save(any(ReconciliationRecord.class));
    }

    @Test
    @DisplayName("Should detect gateway FAILED and auto-refund pending transaction")
    void testReconciliation_gatewayFailed_autoRefunds() {
        when(transactionRepository.findTransactionsForReconciliation(any(), any()))
                .thenReturn(List.of(pendingTxn));

        MockPaymentGatewayService.GatewayTransactionRecord gwRecord =
                new MockPaymentGatewayService.GatewayTransactionRecord(
                        pendingTxn.getTransactionRef(),
                        "GW_FAILED_666",
                        new BigDecimal("499.00"),
                        testBiller.getCode(),
                        "12345",
                        PaymentStatus.FAILED
                );
        when(gatewayService.queryGatewayStatus(pendingTxn.getTransactionRef()))
                .thenReturn(Optional.of(gwRecord));

        when(paymentService.rollbackAndFail(eq("txn-recon-1"), anyString(), eq("GW_FAILED_666"), eq("05")))
                .thenReturn(pendingTxn);
        when(paymentService.toDto(any())).thenReturn(new PaymentResponse());
        when(reconciliationRepository.save(any(ReconciliationRecord.class))).thenAnswer(i -> {
            ReconciliationRecord r = i.getArgument(0);
            r.setId(102L);
            return r;
        });

        ReconciliationSummaryDto summary = reconciliationService.runReconciliation();

        assertThat(summary.getAutoResolvedCount()).isEqualTo(1);
        verify(paymentService).rollbackAndFail(eq("txn-recon-1"), anyString(), eq("GW_FAILED_666"), eq("05"));
    }
}
