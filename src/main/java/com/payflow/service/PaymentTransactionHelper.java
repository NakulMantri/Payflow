package com.payflow.service;

import com.payflow.dto.PaymentInitiateRequest;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.LedgerAccountType;
import com.payflow.enums.PaymentStatus;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.ResourceNotFoundException;
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
public class PaymentTransactionHelper {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionHelper.class);

    private final PaymentTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    public PaymentTransactionHelper(PaymentTransactionRepository transactionRepository,
                                    WalletRepository walletRepository,
                                    LedgerService ledgerService) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
    }

    /**
     * Atomically locks wallet row (SELECT ... FOR UPDATE), verifies balance, debits,
     * creates transaction in PENDING_GATEWAY status, and records double-entry ledger.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentTransaction prepareAndLockPayment(User user, Biller biller, String idempotencyKey,
                                                    PaymentInitiateRequest request, int maxRetries) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + user.getId()));

        if (!wallet.hasSufficientBalance(request.getAmount())) {
            throw new InsufficientBalanceException("Insufficient wallet balance. Available: ₹" +
                    wallet.getBalance() + ", Requested: ₹" + request.getAmount());
        }

        // 1. Debit wallet
        wallet.debit(request.getAmount());
        wallet = walletRepository.save(wallet);

        // 2. Create transaction record
        String txnId = UUID.randomUUID().toString();
        String txnRef = "TXN_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentTransaction txn = new PaymentTransaction();
        txn.setId(txnId);
        txn.setTransactionRef(txnRef);
        txn.setIdempotencyKey(idempotencyKey);
        txn.setUser(user);
        txn.setWallet(wallet);
        txn.setBiller(biller);
        txn.setConsumerNumber(request.getConsumerNumber());
        txn.setAmount(request.getAmount());
        txn.setFee(BigDecimal.ZERO);
        txn.setCurrency(wallet.getCurrency());
        txn.setStatus(PaymentStatus.PENDING_GATEWAY);
        txn.setMaxRetries(maxRetries);
        txn = transactionRepository.save(txn);

        // 3. Double-Entry: DEBIT USER_WALLET, CREDIT BILLER_ESCROW
        ledgerService.recordDoubleEntry(
                txn,
                LedgerAccountType.USER_WALLET,
                wallet.getId().toString(),
                wallet.getBalance(),
                LedgerAccountType.BILLER_ESCROW,
                biller.getEscrowAccountId(),
                BigDecimal.ZERO,
                request.getAmount(),
                "Payment to " + biller.getName() + " for " + request.getConsumerNumber()
        );

        log.info("Committed locked payment preparation: txnRef={}, user={}, balance={}",
                txnRef, user.getId(), wallet.getBalance());
        return txn;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentTransaction finalizeSuccess(String transactionId, String gatewayRef, String gatewayCode) {
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        txn.markSuccess(gatewayRef, gatewayCode);
        return transactionRepository.save(txn);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentTransaction scheduleRetry(String transactionId, int attempt, LocalDateTime nextRetry, String reason) {
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        txn.setRetryCount(attempt);
        txn.setNextRetryAt(nextRetry);
        txn.setStatus(PaymentStatus.RETRYING);
        txn.setFailureReason("Retry " + attempt + ": " + reason);
        return transactionRepository.save(txn);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentTransaction rollbackAndFail(String transactionId, String reason, String gatewayRef, String gatewayCode) {
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (txn.getStatus() == PaymentStatus.FAILED || txn.getStatus() == PaymentStatus.REFUNDED) {
            return txn;
        }

        Long userId = txn.getUser().getId();
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for user: " + userId));
        wallet.credit(txn.getAmount());
        Wallet savedWallet = walletRepository.save(wallet);

        txn.markFailed(reason, gatewayRef, gatewayCode);
        PaymentTransaction updatedTxn = transactionRepository.save(txn);

        // Reverse double-entry: CREDIT USER_WALLET, DEBIT BILLER_ESCROW
        ledgerService.recordDoubleEntry(
                updatedTxn,
                LedgerAccountType.BILLER_ESCROW,
                updatedTxn.getBiller().getEscrowAccountId(),
                BigDecimal.ZERO,
                LedgerAccountType.USER_WALLET,
                savedWallet.getId().toString(),
                savedWallet.getBalance(),
                updatedTxn.getAmount(),
                "Refund for failed payment to " + updatedTxn.getBiller().getName()
        );

        log.info("Rolled back failed payment: txnRef={}, refunded_amount={}, new_wallet_balance={}",
                updatedTxn.getTransactionRef(), updatedTxn.getAmount(), savedWallet.getBalance());
        return updatedTxn;
    }
}
