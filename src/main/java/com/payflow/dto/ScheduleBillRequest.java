package com.payflow.dto;

import com.payflow.enums.BillFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ScheduleBillRequest {

    @NotNull(message = "Biller ID is required")
    private Long billerId;

    @NotBlank(message = "Consumer number is required")
    private String consumerNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum amount is 1.00")
    private BigDecimal amount;

    @NotNull(message = "Frequency is required")
    private BillFrequency frequency;

    private LocalDate dueDate;
    private LocalDateTime nextExecutionDate;

    public ScheduleBillRequest() {}

    public Long getBillerId() { return billerId; }
    public void setBillerId(Long billerId) { this.billerId = billerId; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BillFrequency getFrequency() { return frequency; }
    public void setFrequency(BillFrequency frequency) { this.frequency = frequency; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getNextExecutionDate() { return nextExecutionDate; }
    public void setNextExecutionDate(LocalDateTime nextExecutionDate) { this.nextExecutionDate = nextExecutionDate; }
}
