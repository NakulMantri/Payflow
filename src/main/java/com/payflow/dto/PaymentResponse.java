package com.payflow.dto;

import com.payflow.enums.BillerCategory;
import com.payflow.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {
    private String id;
    private String transactionRef;
    private String idempotencyKey;
    private Long billerId;
    private String billerName;
    private String billerCode;
    private BillerCategory category;
    private String consumerNumber;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private PaymentStatus status;
    private String failureReason;
    private String gatewayReference;
    private String gatewayResponseCode;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime settledAt;

    public PaymentResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getBillerId() { return billerId; }
    public void setBillerId(Long billerId) { this.billerId = billerId; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getBillerCode() { return billerCode; }
    public void setBillerCode(String billerCode) { this.billerCode = billerCode; }

    public BillerCategory getCategory() { return category; }
    public void setCategory(BillerCategory category) { this.category = category; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }

    public String getGatewayResponseCode() { return gatewayResponseCode; }
    public void setGatewayResponseCode(String gatewayResponseCode) { this.gatewayResponseCode = gatewayResponseCode; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
}
