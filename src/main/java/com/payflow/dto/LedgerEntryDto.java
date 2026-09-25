package com.payflow.dto;

import com.payflow.enums.LedgerAccountType;
import com.payflow.enums.LedgerEntryType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LedgerEntryDto {
    private Long id;
    private String transactionId;
    private LedgerAccountType accountType;
    private String accountId;
    private LedgerEntryType entryType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime createdAt;

    public LedgerEntryDto() {}

    public LedgerEntryDto(Long id, String transactionId, LedgerAccountType accountType, String accountId,
                          LedgerEntryType entryType, BigDecimal amount, String currency,
                          BigDecimal balanceAfter, String description, LocalDateTime createdAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountType = accountType;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public LedgerAccountType getAccountType() { return accountType; }
    public void setAccountType(LedgerAccountType accountType) { this.accountType = accountType; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public LedgerEntryType getEntryType() { return entryType; }
    public void setEntryType(LedgerEntryType entryType) { this.entryType = entryType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
