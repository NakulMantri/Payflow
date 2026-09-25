package com.payflow.entity;

import com.payflow.enums.PaymentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_txn_user_id", columnList = "user_id"),
    @Index(name = "idx_txn_status", columnList = "status"),
    @Index(name = "idx_txn_idempotency_key", columnList = "idempotency_key"),
    @Index(name = "idx_txn_created_at", columnList = "created_at"),
    @Index(name = "idx_txn_retry", columnList = "status, next_retry_at")
})
public class PaymentTransaction {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 64)
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    @Column(name = "consumer_number", nullable = false, length = 100)
    private String consumerNumber;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "gateway_reference", length = 100)
    private String gatewayReference;

    @Column(name = "gateway_response_code", length = 50)
    private String gatewayResponseCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PaymentTransaction() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementRetry(LocalDateTime nextRetry) {
        this.retryCount++;
        this.nextRetryAt = nextRetry;
        this.status = PaymentStatus.RETRYING;
    }

    public void markSuccess(String gatewayRef, String gatewayCode) {
        this.status = PaymentStatus.SUCCESS;
        this.gatewayReference = gatewayRef;
        this.gatewayResponseCode = gatewayCode;
        this.settledAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    public void markFailed(String reason, String gatewayRef, String gatewayCode) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.gatewayReference = gatewayRef;
        this.gatewayResponseCode = gatewayCode;
        this.nextRetryAt = null;
    }

    public void markRefunded(String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.failureReason = reason;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Wallet getWallet() { return wallet; }
    public void setWallet(Wallet wallet) { this.wallet = wallet; }

    public Biller getBiller() { return biller; }
    public void setBiller(Biller biller) { this.biller = biller; }

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

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
