package com.payflow.service;

import com.payflow.dto.PaymentInitiateRequest;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.Role;
import com.payflow.repository.PaymentTransactionRepository;
import com.payflow.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionHelperTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerService ledgerService;

    private PaymentTransactionHelper helper;

    private User testUser;
    private Wallet testWallet;
    private Biller testBiller;

    @BeforeEach
    void setUp() {
        helper = new PaymentTransactionHelper(transactionRepository, walletRepository, ledgerService);

        testUser = new User("helper@payflow.com", "pass", "Helper User", "9999999999", Role.ROLE_USER);
        testUser.setId(7L);

        testWallet = new Wallet(testUser, "INR", new BigDecimal("1000.0000"));
        testWallet.setId(70L);

        testBiller = new Biller("ACT", "ACT Fibernet", BillerCategory.BROADBAND, ".*", "ID", BigDecimal.ZERO, "ESC_ACT");
        testBiller.setId(700L);
    }

    @Test
    @DisplayName("Should lock wallet, debit balance, and record double-entry ledger")
    void testPrepareAndLockPayment_success() {
        PaymentInitiateRequest request = new PaymentInitiateRequest(700L, "ACT12345", new BigDecimal("400.00"), false, null);

        when(walletRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentTransaction txn = helper.prepareAndLockPayment(testUser, testBiller, "idemp_h_1", request, 3);

        assertThat(txn).isNotNull();
        assertThat(txn.getAmount()).isEqualByComparingTo("400.00");
        assertThat(txn.getStatus()).isEqualTo(PaymentStatus.PENDING_GATEWAY);
        assertThat(testWallet.getBalance()).isEqualByComparingTo("600.0000");

        verify(ledgerService).recordDoubleEntry(
                any(), any(), anyString(), any(),
                any(), anyString(), any(),
                eq(new BigDecimal("400.00")),
                anyString()
        );
    }

    @Test
    @DisplayName("Should rollback failed transaction, refund wallet balance, and reverse ledger entry")
    void testRollbackAndFail_refundsWallet() {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setId("txn-fail-1");
        txn.setTransactionRef("TXN_FAIL_001");
        txn.setUser(testUser);
        txn.setBiller(testBiller);
        txn.setAmount(new BigDecimal("400.00"));
        txn.setStatus(PaymentStatus.PENDING_GATEWAY);

        when(transactionRepository.findById("txn-fail-1")).thenReturn(Optional.of(txn));
        when(walletRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentTransaction failedTxn = helper.rollbackAndFail("txn-fail-1", "Decline", "GW_DEC", "05");

        assertThat(failedTxn.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(testWallet.getBalance()).isEqualByComparingTo("1400.0000"); // refunded 400

        verify(ledgerService).recordDoubleEntry(
                any(), any(), anyString(), any(),
                any(), anyString(), any(),
                eq(new BigDecimal("400.00")),
                contains("Refund")
        );
    }
}
