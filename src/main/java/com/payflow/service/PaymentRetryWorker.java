package com.payflow.service;

import com.payflow.dto.PaymentNotificationEvent;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.enums.PaymentStatus;
import com.payflow.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentRetryWorker.class);

    private final PaymentTransactionRepository transactionRepository;
    private final MockPaymentGatewayService gatewayService;
    private final PaymentService paymentService;
    private final NotificationProducerService notificationProducer;
    private final IdempotencyService idempotencyService;

    private final int maxRetries;
    private final long initialBackoffMs;
    private final double backoffMultiplier;

    public PaymentRetryWorker(
            PaymentTransactionRepository transactionRepository,
            MockPaymentGatewayService gatewayService,
            PaymentService paymentService,
            NotificationProducerService notificationProducer,
            IdempotencyService idempotencyService,
            @Value("${payflow.retry.max-attempts:3}") int maxRetries,
            @Value("${payflow.retry.initial-backoff-ms:2000}") long initialBackoffMs,
            @Value("${payflow.retry.multiplier:2.0}") double backoffMultiplier) {
        this.transactionRepository = transactionRepository;
        this.gatewayService = gatewayService;
        this.paymentService = paymentService;
        this.notificationProducer = notificationProducer;
        this.idempotencyService = idempotencyService;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * Periodic background worker for retry queue with exponential backoff.
     * Checks for RETRYING transactions whose nextRetryAt timestamp has arrived.
     */
    @Scheduled(fixedDelayString = "${payflow.retry.poll-interval-ms:2000}")
    public void processRetryQueue() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentTransaction> retryingList = transactionRepository.findPendingRetries(PaymentStatus.RETRYING, now);

        if (!retryingList.isEmpty()) {
            log.info("PaymentRetryWorker found {} transactions due for retry", retryingList.size());
        }

        for (PaymentTransaction txn : retryingList) {
            try {
                processSingleRetry(txn);
            } catch (Exception e) {
                log.error("Error processing retry for txnRef={}: {}", txn.getTransactionRef(), e.getMessage(), e);
            }
        }
    }

    public void processSingleRetry(PaymentTransaction txn) {
        int currentAttempt = txn.getRetryCount() + 1;
        log.info("Processing retry attempt {}/{} for txnRef={}", currentAttempt, maxRetries, txn.getTransactionRef());

        // 1. Inquire external gateway settlement registry first (in case it settled during timeout)
        Optional<MockPaymentGatewayService.GatewayTransactionRecord> settledCheck =
                gatewayService.queryGatewayStatus(txn.getTransactionRef());

        if (settledCheck.isPresent() && settledCheck.get().getStatus() == PaymentStatus.SUCCESS) {
            log.info("RetryWorker: Found settled record on gateway for txnRef={}", txn.getTransactionRef());
            PaymentTransaction successTxn = paymentService.finalizeSuccess(
                    txn.getId(),
                    settledCheck.get().getGatewayReference(),
                    "00"
            );
            idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(successTxn));
            dispatchSuccessNotification(successTxn);
            return;
        }

        // 2. If not settled, re-attempt call to gateway
        MockPaymentGatewayService.GatewayResult gwResult = gatewayService.processPayment(
                txn.getTransactionRef(),
                txn.getAmount(),
                txn.getBiller().getCode(),
                txn.getConsumerNumber()
        );

        if (gwResult.getOutcome() == MockPaymentGatewayService.GatewayOutcome.SUCCESS) {
            PaymentTransaction successTxn = paymentService.finalizeSuccess(
                    txn.getId(),
                    gwResult.getGatewayReference(),
                    gwResult.getResponseCode()
            );
            idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(successTxn));
            dispatchSuccessNotification(successTxn);
            log.info("Retry SUCCESS for txnRef={} on attempt {}", txn.getTransactionRef(), currentAttempt);

        } else if (gwResult.getOutcome() == MockPaymentGatewayService.GatewayOutcome.TIMEOUT) {
            if (currentAttempt < maxRetries) {
                // Calculate next exponential backoff: initial * multiplier^(currentAttempt)
                long backoffDelay = (long) (initialBackoffMs * Math.pow(backoffMultiplier, currentAttempt));
                LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(backoffDelay * 1_000_000L);

                paymentService.scheduleRetry(txn.getId(), currentAttempt, nextRetryAt, gwResult.getMessage());
                log.warn("Retry {}/{} failed with timeout for txnRef={}, scheduled next retry at {}",
                        currentAttempt, maxRetries, txn.getTransactionRef(), nextRetryAt);
            } else {
                // Max retries exhausted! Rollback and refund user
                log.error("Max retries ({}) exhausted for txnRef={}. Initiating automatic refund.",
                        maxRetries, txn.getTransactionRef());
                PaymentTransaction failedTxn = paymentService.rollbackAndFail(
                        txn.getId(),
                        "Max retries (" + maxRetries + ") exhausted: " + gwResult.getMessage(),
                        gwResult.getGatewayReference(),
                        gwResult.getResponseCode()
                );
                idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(failedTxn));
                dispatchFailureNotification(failedTxn, "Payment failed after " + maxRetries + " retry attempts. Refunded to wallet.");
            }
        } else {
            // Permanent decline
            log.warn("Retry resulted in hard decline for txnRef={}: {}", txn.getTransactionRef(), gwResult.getMessage());
            PaymentTransaction failedTxn = paymentService.rollbackAndFail(
                    txn.getId(),
                    gwResult.getMessage(),
                    gwResult.getGatewayReference(),
                    gwResult.getResponseCode()
            );
            idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(failedTxn));
            dispatchFailureNotification(failedTxn, gwResult.getMessage());
        }
    }

    private void dispatchSuccessNotification(PaymentTransaction txn) {
        User user = txn.getUser();
        notificationProducer.publishPaymentNotification(new PaymentNotificationEvent(
                UUID.randomUUID().toString(),
                "PAYMENT_SUCCESS",
                txn.getTransactionRef(),
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                txn.getBiller().getName(),
                txn.getConsumerNumber(),
                txn.getAmount(),
                txn.getCurrency(),
                PaymentStatus.SUCCESS.name(),
                "Payment confirmed after retry verification",
                LocalDateTime.now()
        ));
    }

    private void dispatchFailureNotification(PaymentTransaction txn, String reason) {
        User user = txn.getUser();
        notificationProducer.publishPaymentNotification(new PaymentNotificationEvent(
                UUID.randomUUID().toString(),
                "PAYMENT_FAILED",
                txn.getTransactionRef(),
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                txn.getBiller().getName(),
                txn.getConsumerNumber(),
                txn.getAmount(),
                txn.getCurrency(),
                PaymentStatus.FAILED.name(),
                reason,
                LocalDateTime.now()
        ));
    }
}
