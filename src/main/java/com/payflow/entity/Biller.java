package com.payflow.entity;

import com.payflow.enums.BillerCategory;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "billers", indexes = {
    @Index(name = "idx_billers_category", columnList = "category"),
    @Index(name = "idx_billers_code", columnList = "code")
})
public class Biller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BillerCategory category;

    @Column(name = "account_number_regex")
    private String accountNumberRegex;

    @Column(name = "account_number_label", nullable = false)
    private String accountNumberLabel = "Consumer Number";

    @Column(name = "commission_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Column(name = "escrow_account_id", nullable = false, length = 100)
    private String escrowAccountId;

    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Biller() {}

    public Biller(String code, String name, BillerCategory category, String accountNumberRegex,
                  String accountNumberLabel, BigDecimal commissionRate, String escrowAccountId) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.accountNumberRegex = accountNumberRegex;
        this.accountNumberLabel = accountNumberLabel != null ? accountNumberLabel : "Consumer Number";
        this.commissionRate = commissionRate != null ? commissionRate : BigDecimal.ZERO;
        this.escrowAccountId = escrowAccountId;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BillerCategory getCategory() { return category; }
    public void setCategory(BillerCategory category) { this.category = category; }

    public String getAccountNumberRegex() { return accountNumberRegex; }
    public void setAccountNumberRegex(String accountNumberRegex) { this.accountNumberRegex = accountNumberRegex; }

    public String getAccountNumberLabel() { return accountNumberLabel; }
    public void setAccountNumberLabel(String accountNumberLabel) { this.accountNumberLabel = accountNumberLabel; }

    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }

    public String getEscrowAccountId() { return escrowAccountId; }
    public void setEscrowAccountId(String escrowAccountId) { this.escrowAccountId = escrowAccountId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
