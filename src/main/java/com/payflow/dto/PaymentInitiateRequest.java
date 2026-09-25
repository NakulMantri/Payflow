package com.payflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PaymentInitiateRequest {

    @NotNull(message = "Biller ID is required")
    private Long billerId;

    @NotBlank(message = "Consumer number is required")
    private String consumerNumber;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "1.00", message = "Minimum payment amount is 1.00")
    private BigDecimal amount;

    private boolean saveAccount = false;
    private String accountNickname;

    public PaymentInitiateRequest() {}

    public PaymentInitiateRequest(Long billerId, String consumerNumber, BigDecimal amount, boolean saveAccount, String accountNickname) {
        this.billerId = billerId;
        this.consumerNumber = consumerNumber;
        this.amount = amount;
        this.saveAccount = saveAccount;
        this.accountNickname = accountNickname;
    }

    public Long getBillerId() { return billerId; }
    public void setBillerId(Long billerId) { this.billerId = billerId; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public boolean isSaveAccount() { return saveAccount; }
    public void setSaveAccount(boolean saveAccount) { this.saveAccount = saveAccount; }

    public String getAccountNickname() { return accountNickname; }
    public void setAccountNickname(String accountNickname) { this.accountNickname = accountNickname; }
}
