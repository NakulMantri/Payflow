package com.payflow.entity;

import com.payflow.enums.LedgerAccountType;
import com.payflow.enums.LedgerEntryType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_txn_id", columnList = "transaction_id"),
    @Index(name = "idx_ledger_acc", columnList = "account_type, account_id"),
    @Index(name = "idx_ledger_created", columnList = "created_at")
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private PaymentTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 50)
    private LedgerAccountType accountType;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "balance_after", precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public LedgerEntry() {}

    public LedgerEntry(PaymentTransaction transaction, LedgerAccountType accountType, String accountId,
                       LedgerEntryType entryType, BigDecimal amount, String currency,
                       BigDecimal balanceAfter, String description) {
        this.transaction = transaction;
        this.accountType = accountType;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency != null ? currency : "INR";
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PaymentTransaction getTransaction() { return transaction; }
    public void setTransaction(PaymentTransaction transaction) { this.transaction = transaction; }

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
