package com.payflow.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WebhookPayload {
    private String eventId;
    private String eventType; // e.g. "payment.success", "payment.failed"
    private String gatewayReference;
    private String transactionRef;
    private BigDecimal amount;
    private String currency;
    private String status; // "SUCCESS", "FAILED"
    private String responseCode;
    private String message;
    private LocalDateTime timestamp;

    public WebhookPayload() {}

    public WebhookPayload(String eventId, String eventType, String gatewayReference, String transactionRef,
                          BigDecimal amount, String currency, String status, String responseCode,
                          String message, LocalDateTime timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.gatewayReference = gatewayReference;
        this.transactionRef = transactionRef;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.responseCode = responseCode;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
