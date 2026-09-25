package com.payflow.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletResponse {
    private Long id;
    private Long userId;
    private String currency;
    private BigDecimal balance;
    private String status;
    private Long version;
    private LocalDateTime updatedAt;

    public WalletResponse() {}

    public WalletResponse(Long id, Long userId, String currency, BigDecimal balance, String status, Long version, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
