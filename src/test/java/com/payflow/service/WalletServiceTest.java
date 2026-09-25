package com.payflow.service;

import com.payflow.dto.WalletResponse;
import com.payflow.dto.WalletTopUpRequest;
import com.payflow.entity.Biller;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.Role;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.repository.BillerRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private BillerRepository billerRepository;

    @Mock
    private LedgerService ledgerService;

    private WalletService walletService;

    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository, billerRepository, ledgerService);

        testUser = new User("user@payflow.com", "pass", "John Doe", "9999999999", Role.ROLE_USER);
        testUser.setId(2L);

        testWallet = new Wallet(testUser, "INR", new BigDecimal("500.0000"));
        testWallet.setId(20L);
    }

    @Test
    @DisplayName("Should top up wallet balance and record double-entry ledger")
    void testTopUp_success() {
        WalletTopUpRequest request = new WalletTopUpRequest(new BigDecimal("1500.00"), "UPI", "upi_ref_123");

        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));
        when(billerRepository.findByCode("PAYFLOW_TOPUP")).thenReturn(Optional.empty());
        when(billerRepository.save(any(Biller.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WalletResponse response = walletService.topUp(testUser, request);

        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualByComparingTo("2000.0000");

        // Verify ledger recorded
        verify(ledgerService).recordDoubleEntry(
                any(), any(), anyString(), any(),
                any(), anyString(), any(),
                eq(new BigDecimal("1500.00")),
                anyString()
        );
    }

    @Test
    @DisplayName("Should debit balance successfully when funds are sufficient")
    void testDebitWithLock_success() {
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

        Wallet updated = walletService.debitWithLock(2L, new BigDecimal("200.00"));

        assertThat(updated.getBalance()).isEqualByComparingTo("300.0000");
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when debit amount exceeds balance")
    void testDebitWithLock_insufficient() {
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(testWallet));

        assertThatThrownBy(() -> walletService.debitWithLock(2L, new BigDecimal("800.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient funds");

        verify(walletRepository, never()).save(any());
    }
}
