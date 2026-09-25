package com.payflow.service;

import com.payflow.dto.CreateBillerAccountRequest;
import com.payflow.dto.PaymentInitiateRequest;
import com.payflow.dto.PaymentNotificationEvent;
import com.payflow.dto.PaymentResponse;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.LedgerAccountType;
import com.payflow.enums.PaymentStatus;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.PayflowException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.PaymentTransactionRepository;
import com.payflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BillerService billerService;
    private final LedgerService ledgerService;
    private final MockPaymentGatewayService gatewayService;
    private final IdempotencyService idempotencyService;
    private final NotificationProducerService notificationProducer;
    private final RateLimiterService rateLimiterService;
    private final PaymentTransactionHelper txHelper;

    private final int maxRetries;
    private final long initialBackoffMs;

    public PaymentService(
            PaymentTransactionRepository transactionRepository,
            WalletRepository walletRepository,
            BillerService billerService,
            LedgerService ledgerService,
            MockPaymentGatewayService gatewayService,
            IdempotencyService idempotencyService,
            NotificationProducerService notificationProducer,
            RateLimiterService rateLimiterService,
            PaymentTransactionHelper txHelper,
            @Value("${payflow.retry.max-attempts:3}") int maxRetries,
            @Value("${payflow.retry.initial-backoff-ms:2000}") long initialBackoffMs) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.billerService = billerService;
        this.ledgerService = ledgerService;
        this.gatewayService = gatewayService;
        this.idempotencyService = idempotencyService;
        this.notificationProducer = notificationProducer;
        this.rateLimiterService = rateLimiterService;
        this.txHelper = txHelper;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * Executes payment with:
     * 1. Rate limiting enforcement
     * 2. Atomic Idempotency lock (prevent double charge)
     * 3. Row-level locked DB balance deduction (concurrency safe)
     * 4. Double-entry ledger recording
     * 5. External mock gateway call
     * 6. Transient timeout handling & retry queue placement
     * 7. Automatic rollback & refund on hard failure
     * 8. Async notification dispatch via Kafka
     */
    public PaymentResponse processPayment(User user, String idempotencyKey, PaymentInitiateRequest request) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new PayflowException("Idempotency-Key header is mandatory for payment execution");
        }

        // 1. Rate Limiting Check
        rateLimiterService.checkLimit("payment:user:" + user.getId());

        // 2. Idempotency Check & Atomic Lock Acquisition
        IdempotencyService.IdempotencyCheckResult checkResult =
                idempotencyService.acquireOrCheck(idempotencyKey, user.getId(), request);

        if (checkResult.isCached()) {
            log.info("Returning cached payment response for idempotencyKey={}", idempotencyKey);
            return checkResult.getCachedResponse();
        }

        Biller biller = billerService.getBillerEntity(request.getBillerId());
        billerService.validateConsumerNumber(biller, request.getConsumerNumber());

        if (request.isSaveAccount()) {
            try {
                billerService.saveBillerAccount(user, new CreateBillerAccountRequest(
                        biller.getId(),
                        request.getConsumerNumber(),
                        request.getAccountNickname() != null ? request.getAccountNickname() : biller.getName()
                ));
            } catch (Exception e) {
                // Non-fatal if account already saved
                log.debug("Biller account already saved: {}", e.getMessage());
            }
        }

        // 3. Prepare transaction & debit wallet under row-level lock (DB Transaction boundary)
        PaymentTransaction txn;
        try {
            txn = txHelper.prepareAndLockPayment(user, biller, idempotencyKey, request, maxRetries);
        } catch (Exception ex) {
            // Release idempotency lock if initial reservation failed (e.g. insufficient funds)
            idempotencyService.releaseLock(idempotencyKey);
            throw ex;
        }

        // 4. Call Mock Payment Gateway
        MockPaymentGatewayService.GatewayResult gwResult;
        try {
            gwResult = gatewayService.processPayment(
                    txn.getTransactionRef(),
                    txn.getAmount(),
                    biller.getCode(),
                    txn.getConsumerNumber()
            );
        } catch (Exception ex) {
            log.error("Exception calling payment gateway for txnRef={}: {}", txn.getTransactionRef(), ex.getMessage());
            gwResult = new MockPaymentGatewayService.GatewayResult(
                    MockPaymentGatewayService.GatewayOutcome.TIMEOUT,
                    null,
                    "99",
                    "Gateway communication failure: " + ex.getMessage()
            );
        }

        // 5. Handle Gateway Outcome
        PaymentResponse response;
        if (gwResult.getOutcome() == MockPaymentGatewayService.GatewayOutcome.SUCCESS) {
            txn = txHelper.finalizeSuccess(txn.getId(), gwResult.getGatewayReference(), gwResult.getResponseCode());
            response = toDto(txn);
            idempotencyService.recordSuccess(idempotencyKey, user.getId(), response);

            // Publish Kafka Success Event
            notificationProducer.publishPaymentNotification(new PaymentNotificationEvent(
                    UUID.randomUUID().toString(),
                    "PAYMENT_SUCCESS",
                    txn.getTransactionRef(),
                    user.getId(),
                    user.getEmail(),
                    user.getPhone(),
                    biller.getName(),
                    txn.getConsumerNumber(),
                    txn.getAmount(),
                    txn.getCurrency(),
                    PaymentStatus.SUCCESS.name(),
                    "Payment confirmed by " + biller.getName(),
                    LocalDateTime.now()
            ));

        } else if (gwResult.getOutcome() == MockPaymentGatewayService.GatewayOutcome.TIMEOUT) {
            // Transient timeout -> queue for automatic retry with exponential backoff
            LocalDateTime nextRetry = LocalDateTime.now().plusNanos(initialBackoffMs * 1_000_000L);
            txn = txHelper.scheduleRetry(txn.getId(), 1, nextRetry, gwResult.getMessage());
            response = toDto(txn);

            log.warn("Payment timed out at gateway; queued for retry: txnRef={}, nextRetry={}",
                    txn.getTransactionRef(), nextRetry);

        } else {
            // Permanent decline -> rollback wallet & reverse ledger entry
            txn = txHelper.rollbackAndFail(txn.getId(), gwResult.getMessage(), gwResult.getGatewayReference(), gwResult.getResponseCode());
            response = toDto(txn);
            idempotencyService.recordSuccess(idempotencyKey, user.getId(), response);

            // Publish Kafka Failure Event
            notificationProducer.publishPaymentNotification(new PaymentNotificationEvent(
                    UUID.randomUUID().toString(),
                    "PAYMENT_FAILED",
                    txn.getTransactionRef(),
                    user.getId(),
                    user.getEmail(),
                    user.getPhone(),
                    biller.getName(),
                    txn.getConsumerNumber(),
                    txn.getAmount(),
                    txn.getCurrency(),
                    PaymentStatus.FAILED.name(),
                    gwResult.getMessage(),
                    LocalDateTime.now()
            ));
        }

        return response;
    }

    public PaymentTransaction finalizeSuccess(String transactionId, String gatewayRef, String gatewayCode) {
        return txHelper.finalizeSuccess(transactionId, gatewayRef, gatewayCode);
    }

    public PaymentTransaction scheduleRetry(String transactionId, int attempt, LocalDateTime nextRetry, String reason) {
        return txHelper.scheduleRetry(transactionId, attempt, nextRetry, reason);
    }

    public PaymentTransaction rollbackAndFail(String transactionId, String reason, String gatewayRef, String gatewayCode) {
        return txHelper.rollbackAndFail(transactionId, reason, gatewayRef, gatewayCode);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getTransactionById(String id) {
        PaymentTransaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return toDto(txn);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(t -> !"PAYFLOW_TOPUP".equals(t.getBiller().getCode()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public PaymentResponse toDto(PaymentTransaction txn) {
        PaymentResponse dto = new PaymentResponse();
        dto.setId(txn.getId());
        dto.setTransactionRef(txn.getTransactionRef());
        dto.setIdempotencyKey(txn.getIdempotencyKey());
        dto.setBillerId(txn.getBiller().getId());
        dto.setBillerName(txn.getBiller().getName());
        dto.setBillerCode(txn.getBiller().getCode());
        dto.setCategory(txn.getBiller().getCategory());
        dto.setConsumerNumber(txn.getConsumerNumber());
        dto.setAmount(txn.getAmount());
        dto.setFee(txn.getFee());
        dto.setCurrency(txn.getCurrency());
        dto.setStatus(txn.getStatus());
        dto.setFailureReason(txn.getFailureReason());
        dto.setGatewayReference(txn.getGatewayReference());
        dto.setGatewayResponseCode(txn.getGatewayResponseCode());
        dto.setRetryCount(txn.getRetryCount());
        dto.setCreatedAt(txn.getCreatedAt());
        dto.setSettledAt(txn.getSettledAt());
        return dto;
    }
}
