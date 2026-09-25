package com.payflow.service;

import com.payflow.dto.WalletResponse;
import com.payflow.dto.WalletTopUpRequest;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.LedgerAccountType;
import com.payflow.enums.PaymentStatus;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.BillerRepository;
import com.payflow.repository.PaymentTransactionRepository;
import com.payflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final BillerRepository billerRepository;
    private final LedgerService ledgerService;

    public WalletService(WalletRepository walletRepository,
                         PaymentTransactionRepository transactionRepository,
                         BillerRepository billerRepository,
                         LedgerService ledgerService) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.billerRepository = billerRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user id: " + userId));
        return toDto(wallet);
    }

    @Transactional(readOnly = true)
    public Wallet getWalletEntity(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user id: " + userId));
    }

    /**
     * Acquires a row-level lock (SELECT FOR UPDATE) on the wallet, validates balance, and debits.
     * Guaranteed safe against concurrent double-spending.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Wallet debitWithLock(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user id: " + userId));

        if (!wallet.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException("Insufficient funds. Available: ₹" + wallet.getBalance() + ", Requested: ₹" + amount);
        }

        wallet.debit(amount);
        Wallet saved = walletRepository.save(wallet);
        log.info("Debited wallet user_id={}, amount={}, new_balance={}", userId, amount, saved.getBalance());
        return saved;
    }

    /**
     * Acquires a row-level lock and credits the wallet balance (for top-ups and refunds).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Wallet creditWithLock(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user id: " + userId));

        wallet.credit(amount);
        Wallet saved = walletRepository.save(wallet);
        log.info("Credited wallet user_id={}, amount={}, new_balance={}", userId, amount, saved.getBalance());
        return saved;
    }

    /**
     * Top-up user wallet balance and record corresponding double-entry ledger records.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletResponse topUp(User user, WalletTopUpRequest request) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user id: " + user.getId()));

        wallet.credit(request.getAmount());
        Wallet updatedWallet = walletRepository.save(wallet);

        // Find or create synthetic internal biller for TOP_UP
        Biller internalBiller = billerRepository.findByCode("PAYFLOW_TOPUP")
                .orElseGet(() -> {
                    Biller b = new Biller(
                            "PAYFLOW_TOPUP",
                            "PayFlow Wallet TopUp",
                            BillerCategory.ELECTRICITY, // placeholder
                            ".*",
                            "Account",
                            BigDecimal.ZERO,
                            "ESCROW_PAYFLOW_TOPUP"
                    );
                    return billerRepository.save(b);
                });

        // Create transaction record for top-up
        String txnId = UUID.randomUUID().toString();
        String txnRef = "TOPUP_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String idempKey = request.getReference() != null ? request.getReference() : "topup_" + txnId;

        PaymentTransaction txn = new PaymentTransaction();
        txn.setId(txnId);
        txn.setTransactionRef(txnRef);
        txn.setIdempotencyKey(idempKey);
        txn.setUser(user);
        txn.setWallet(updatedWallet);
        txn.setBiller(internalBiller);
        txn.setConsumerNumber("WALLET_TOPUP");
        txn.setAmount(request.getAmount());
        txn.setFee(BigDecimal.ZERO);
        txn.setCurrency(wallet.getCurrency());
        txn.setStatus(PaymentStatus.SUCCESS);
        txn.setSettledAt(LocalDateTime.now());
        txn.setGatewayReference(request.getPaymentMethod() + "_" + System.currentTimeMillis());
        txn = transactionRepository.save(txn);

        // Double-entry: DEBIT GATEWAY_CLEARING, CREDIT USER_WALLET
        ledgerService.recordDoubleEntry(
                txn,
                LedgerAccountType.GATEWAY_CLEARING,
                request.getPaymentMethod(),
                BigDecimal.ZERO,
                LedgerAccountType.USER_WALLET,
                wallet.getId().toString(),
                updatedWallet.getBalance(),
                request.getAmount(),
                "Wallet TopUp via " + request.getPaymentMethod()
        );

        log.info("Completed wallet top-up user_id={}, amount={}, balance={}", user.getId(), request.getAmount(), updatedWallet.getBalance());
        return toDto(updatedWallet);
    }

    public WalletResponse toDto(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getCurrency(),
                wallet.getBalance(),
                wallet.getStatus(),
                wallet.getVersion(),
                wallet.getUpdatedAt()
        );
    }
}
