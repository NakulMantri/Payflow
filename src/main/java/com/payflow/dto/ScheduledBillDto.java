package com.payflow.dto;

import com.payflow.enums.BillFrequency;
import com.payflow.enums.ScheduledBillStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ScheduledBillDto {
    private Long id;
    private Long billerId;
    private String billerName;
    private String billerCode;
    private String consumerNumber;
    private BigDecimal amount;
    private BillFrequency frequency;
    private LocalDate dueDate;
    private LocalDateTime nextExecutionDate;
    private ScheduledBillStatus status;
    private LocalDateTime lastExecutionDate;
    private String lastTransactionId;
    private LocalDateTime createdAt;

    public ScheduledBillDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBillerId() { return billerId; }
    public void setBillerId(Long billerId) { this.billerId = billerId; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getBillerCode() { return billerCode; }
    public void setBillerCode(String billerCode) { this.billerCode = billerCode; }

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

    public ScheduledBillStatus getStatus() { return status; }
    public void setStatus(ScheduledBillStatus status) { this.status = status; }

    public LocalDateTime getLastExecutionDate() { return lastExecutionDate; }
    public void setLastExecutionDate(LocalDateTime lastExecutionDate) { this.lastExecutionDate = lastExecutionDate; }

    public String getLastTransactionId() { return lastTransactionId; }
    public void setLastTransactionId(String lastTransactionId) { this.lastTransactionId = lastTransactionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
