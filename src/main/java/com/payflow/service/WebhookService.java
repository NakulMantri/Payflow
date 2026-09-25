package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.PaymentNotificationEvent;
import com.payflow.dto.WebhookPayload;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.enums.PaymentStatus;
import com.payflow.exception.InvalidSignatureException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final String webhookSecret;
    private final MockPaymentGatewayService gatewayService;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentService paymentService;
    private final NotificationProducerService notificationProducer;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public WebhookService(
            @Value("${payflow.mock-gateway.webhook-secret:pf_webhook_secret_key_987654321}") String webhookSecret,
            MockPaymentGatewayService gatewayService,
            PaymentTransactionRepository transactionRepository,
            PaymentService paymentService,
            NotificationProducerService notificationProducer,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.webhookSecret = webhookSecret;
        this.gatewayService = gatewayService;
        this.transactionRepository = transactionRepository;
        this.paymentService = paymentService;
        this.notificationProducer = notificationProducer;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates HMAC signature and processes external async status update.
     */
    @Transactional
    public void processWebhook(String rawPayload, String signatureHeader) {
        // 1. Verify HMAC-SHA256 Signature
        String calculatedSignature = gatewayService.calculateHmacSha256(rawPayload, webhookSecret);
        if (signatureHeader == null || !calculatedSignature.equalsIgnoreCase(signatureHeader.trim())) {
            log.warn("Webhook signature verification failed: received={}, expected={}", signatureHeader, calculatedSignature);
            throw new InvalidSignatureException("Invalid webhook HMAC-SHA256 signature");
        }

        // 2. Deserialize Payload
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, WebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook payload format: " + e.getMessage());
        }

        log.info("Processing verified gateway webhook: eventId={}, txnRef={}, status={}",
                payload.getEventId(), payload.getTransactionRef(), payload.getStatus());

        PaymentTransaction txn = transactionRepository.findByTransactionRef(payload.getTransactionRef())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for ref: " + payload.getTransactionRef()));

        // Idempotency: if transaction is already in terminal state, ignore
        if (txn.getStatus() == PaymentStatus.SUCCESS || txn.getStatus() == PaymentStatus.FAILED) {
            log.info("Webhook for txnRef={} received but transaction already in terminal state {}",
                    payload.getTransactionRef(), txn.getStatus());
            return;
        }

        if ("SUCCESS".equalsIgnoreCase(payload.getStatus())) {
            PaymentTransaction updated = paymentService.finalizeSuccess(
                    txn.getId(),
                    payload.getGatewayReference(),
                    payload.getResponseCode() != null ? payload.getResponseCode() : "00"
            );
            idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(updated));

            // Notify user
            User user = updated.getUser();
            notificationProducer.publishPaymentNotification(new PaymentNotificationEvent(
                    UUID.randomUUID().toString(),
                    "PAYMENT_SUCCESS",
                    updated.getTransactionRef(),
                    user.getId(),
                    user.getEmail(),
                    user.getPhone(),
                    updated.getBiller().getName(),
                    updated.getConsumerNumber(),
                    updated.getAmount(),
                    updated.getCurrency(),
                    PaymentStatus.SUCCESS.name(),
                    "Webhook confirmation: " + payload.getMessage(),
                    LocalDateTime.now()
            ));

        } else {
            // FAILED
            PaymentTransaction updated = paymentService.rollbackAndFail(
                    txn.getId(),
                    payload.getMessage() != null ? payload.getMessage() : "Payment failed via gateway webhook",
                    payload.getGatewayReference(),
                    payload.getResponseCode() != null ? payload.getResponseCode() : "05"
            );
            idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(updated));

            // Notify user
            User user = updated.getUser();
            notificationProducer.publishPaymentNotification(new PaymentNotificationEvent(
                    UUID.randomUUID().toString(),
                    "PAYMENT_FAILED",
                    updated.getTransactionRef(),
                    user.getId(),
                    user.getEmail(),
                    user.getPhone(),
                    updated.getBiller().getName(),
                    updated.getConsumerNumber(),
                    updated.getAmount(),
                    updated.getCurrency(),
                    PaymentStatus.FAILED.name(),
                    "Webhook confirmation: " + payload.getMessage(),
                    LocalDateTime.now()
            ));
        }
    }
}
