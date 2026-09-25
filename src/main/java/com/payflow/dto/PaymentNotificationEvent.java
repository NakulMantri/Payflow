package com.payflow.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentNotificationEvent {
    private String eventId;
    private String eventType; // PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REFUNDED
    private String transactionRef;
    private Long userId;
    private String userEmail;
    private String userPhone;
    private String billerName;
    private String consumerNumber;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String reason;
    private LocalDateTime timestamp;

    public PaymentNotificationEvent() {}

    public PaymentNotificationEvent(String eventId, String eventType, String transactionRef,
                                  Long userId, String userEmail, String userPhone,
                                  String billerName, String consumerNumber, BigDecimal amount,
                                  String currency, String status, String reason, LocalDateTime timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.transactionRef = transactionRef;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userPhone = userPhone;
        this.billerName = billerName;
        this.consumerNumber = consumerNumber;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
