package com.payflow.dto;

import com.payflow.enums.BillerCategory;
import java.math.BigDecimal;

public class BillerDto {
    private Long id;
    private String code;
    private String name;
    private BillerCategory category;
    private String accountNumberRegex;
    private String accountNumberLabel;
    private BigDecimal commissionRate;
    private String escrowAccountId;
    private String status;

    public BillerDto() {}

    public BillerDto(Long id, String code, String name, BillerCategory category,
                     String accountNumberRegex, String accountNumberLabel,
                     BigDecimal commissionRate, String escrowAccountId, String status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.category = category;
        this.accountNumberRegex = accountNumberRegex;
        this.accountNumberLabel = accountNumberLabel;
        this.commissionRate = commissionRate;
        this.escrowAccountId = escrowAccountId;
        this.status = status;
    }

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
}
