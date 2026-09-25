package com.payflow.dto;

import com.payflow.enums.BillerCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateBillerRequest {

    @NotBlank(message = "Biller code is required")
    private String code;

    @NotBlank(message = "Biller name is required")
    private String name;

    @NotNull(message = "Category is required")
    private BillerCategory category;

    private String accountNumberRegex;
    private String accountNumberLabel = "Consumer Number";
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @NotBlank(message = "Escrow account ID is required")
    private String escrowAccountId;

    public CreateBillerRequest() {}

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
}
