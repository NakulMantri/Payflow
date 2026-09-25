package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.WebhookPayload;
import com.payflow.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockPaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGatewayService.class);

    private final double failureRate;
    private final long timeoutMs;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    // Registry of settled payments in the mock gateway (for reconciliation cross-checks)
    private final Map<String, GatewayTransactionRecord> settlementRegistry = new ConcurrentHashMap<>();

    public MockPaymentGatewayService(
            @Value("${payflow.mock-gateway.failure-rate:0.15}") double failureRate,
            @Value("${payflow.mock-gateway.timeout-ms:200}") long timeoutMs,
            @Value("${payflow.mock-gateway.webhook-secret:pf_webhook_secret_key_987654321}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.failureRate = failureRate;
        this.timeoutMs = timeoutMs;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    public enum GatewayOutcome {
        SUCCESS,
        TIMEOUT,
        FAILED
    }

    public static class GatewayResult {
        private final GatewayOutcome outcome;
        private final String gatewayReference;
        private final String responseCode;
        private final String message;
        private final LocalDateTime timestamp;

        public GatewayResult(GatewayOutcome outcome, String gatewayReference, String responseCode, String message) {
            this.outcome = outcome;
            this.gatewayReference = gatewayReference;
            this.responseCode = responseCode;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        public GatewayOutcome getOutcome() { return outcome; }
        public String getGatewayReference() { return gatewayReference; }
        public String getResponseCode() { return responseCode; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class GatewayTransactionRecord {
        private final String transactionRef;
        private final String gatewayReference;
        private final BigDecimal amount;
        private final String billerCode;
        private final String consumerNumber;
        private PaymentStatus status;
        private final LocalDateTime timestamp;

        public GatewayTransactionRecord(String transactionRef, String gatewayReference, BigDecimal amount,
                                        String billerCode, String consumerNumber, PaymentStatus status) {
            this.transactionRef = transactionRef;
            this.gatewayReference = gatewayReference;
            this.amount = amount;
            this.billerCode = billerCode;
            this.consumerNumber = consumerNumber;
            this.status = status;
            this.timestamp = LocalDateTime.now();
        }

        public String getTransactionRef() { return transactionRef; }
        public String getGatewayReference() { return gatewayReference; }
        public BigDecimal getAmount() { return amount; }
        public String getBillerCode() { return billerCode; }
        public String getConsumerNumber() { return consumerNumber; }
        public PaymentStatus getStatus() { return status; }
        public void setStatus(PaymentStatus status) { this.status = status; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Executes mock payment gateway call with configurable simulation of latency, success, timeouts, and declines.
     */
    public GatewayResult processPayment(String transactionRef, BigDecimal amount, String billerCode, String consumerNumber) {
        // Simulate real-world network roundtrip latency
        if (timeoutMs > 0) {
            try {
                Thread.sleep(Math.min(timeoutMs, 100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String gatewayRef = "GW_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 1. Check for deterministic testing triggers via consumer number suffixes
        if (consumerNumber.endsWith("999")) {
            log.warn("Mock Gateway: Deterministic TIMEOUT triggered for consumerNumber={}", consumerNumber);
            return new GatewayResult(GatewayOutcome.TIMEOUT, gatewayRef, "91", "Switch / Gateway Timeout (transient network fault)");
        }
        if (consumerNumber.endsWith("000")) {
            log.warn("Mock Gateway: Deterministic DECLINE triggered for consumerNumber={}", consumerNumber);
            GatewayTransactionRecord record = new GatewayTransactionRecord(transactionRef, gatewayRef, amount, billerCode, consumerNumber, PaymentStatus.FAILED);
            settlementRegistry.put(transactionRef, record);
            return new GatewayResult(GatewayOutcome.FAILED, gatewayRef, "05", "Biller declined: Invalid consumer account ID");
        }
        if (consumerNumber.endsWith("111")) {
            log.info("Mock Gateway: Deterministic SUCCESS triggered for consumerNumber={}", consumerNumber);
            GatewayTransactionRecord record = new GatewayTransactionRecord(transactionRef, gatewayRef, amount, billerCode, consumerNumber, PaymentStatus.SUCCESS);
            settlementRegistry.put(transactionRef, record);
            return new GatewayResult(GatewayOutcome.SUCCESS, gatewayRef, "00", "Payment Approved and Settled");
        }

        // 2. Randomized simulation based on failure rate
        double roll = random.nextDouble();
        if (roll < failureRate * 0.65) {
            // 65% of failures are transient timeouts
            log.warn("Mock Gateway: Simulated transient TIMEOUT for txnRef={}", transactionRef);
            return new GatewayResult(GatewayOutcome.TIMEOUT, gatewayRef, "91", "Gateway Timeout: Biller switch not responding");
        } else if (roll < failureRate) {
            // 35% of failures are permanent declines
            log.warn("Mock Gateway: Simulated permanent DECLINE for txnRef={}", transactionRef);
            GatewayTransactionRecord record = new GatewayTransactionRecord(transactionRef, gatewayRef, amount, billerCode, consumerNumber, PaymentStatus.FAILED);
            settlementRegistry.put(transactionRef, record);
            return new GatewayResult(GatewayOutcome.FAILED, gatewayRef, "05", "Card/Account declined by issuing institution");
        }

        // Standard Success
        log.info("Mock Gateway: Successful authorization for txnRef={}, gwRef={}", transactionRef, gatewayRef);
        GatewayTransactionRecord record = new GatewayTransactionRecord(transactionRef, gatewayRef, amount, billerCode, consumerNumber, PaymentStatus.SUCCESS);
        settlementRegistry.put(transactionRef, record);
        return new GatewayResult(GatewayOutcome.SUCCESS, gatewayRef, "00", "Payment Approved and Settled");
    }

    /**
     * Gateway status inquiry (for retry worker or reconciliation checks).
     */
    public Optional<GatewayTransactionRecord> queryGatewayStatus(String transactionRef) {
        return Optional.ofNullable(settlementRegistry.get(transactionRef));
    }

    public Map<String, GatewayTransactionRecord> getAllSettledRecords() {
        return Collections.unmodifiableMap(settlementRegistry);
    }

    public void registerSettlementDirectly(String transactionRef, GatewayTransactionRecord record) {
        settlementRegistry.put(transactionRef, record);
    }

    /**
     * Generates a signed webhook payload from mock gateway to simulate async callbacks.
     */
    public WebhookSimulationResult createSimulatedWebhook(String transactionRef, BigDecimal amount, PaymentStatus status) {
        String eventId = "EVT_" + UUID.randomUUID().toString();
        String gwRef = "GW_" + System.currentTimeMillis();
        String eventType = status == PaymentStatus.SUCCESS ? "payment.success" : "payment.failed";
        String responseCode = status == PaymentStatus.SUCCESS ? "00" : "05";
        String message = status == PaymentStatus.SUCCESS ? "Transaction Settled via Webhook" : "Transaction Failed at Gateway";

        WebhookPayload payload = new WebhookPayload(
                eventId,
                eventType,
                gwRef,
                transactionRef,
                amount,
                "INR",
                status.name(),
                responseCode,
                message,
                LocalDateTime.now()
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            jsonPayload = "{}";
        }

        String signature = calculateHmacSha256(jsonPayload, webhookSecret);
        return new WebhookSimulationResult(payload, jsonPayload, signature);
    }

    public String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    public static class WebhookSimulationResult {
        private final WebhookPayload payload;
        private final String rawJson;
        private final String signature;

        public WebhookSimulationResult(WebhookPayload payload, String rawJson, String signature) {
            this.payload = payload;
            this.rawJson = rawJson;
            this.signature = signature;
        }

        public WebhookPayload getPayload() { return payload; }
        public String getRawJson() { return rawJson; }
        public String getSignature() { return signature; }
    }
}
