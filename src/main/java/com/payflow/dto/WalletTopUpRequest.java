package com.payflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class WalletTopUpRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum top-up amount is 1.00")
    private BigDecimal amount;

    private String paymentMethod = "UPI";
    private String reference;

    public WalletTopUpRequest() {}

    public WalletTopUpRequest(BigDecimal amount, String paymentMethod, String reference) {
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.reference = reference;
    }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
