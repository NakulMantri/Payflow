package com.payflow.entity;

import com.payflow.enums.BillFrequency;
import com.payflow.enums.ScheduledBillStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_bills", indexes = {
    @Index(name = "idx_sched_user", columnList = "user_id"),
    @Index(name = "idx_sched_status_date", columnList = "status, next_execution_date")
})
public class ScheduledBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    @Column(name = "consumer_number", nullable = false, length = 100)
    private String consumerNumber;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BillFrequency frequency;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "next_execution_date", nullable = false)
    private LocalDateTime nextExecutionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ScheduledBillStatus status = ScheduledBillStatus.ACTIVE;

    @Column(name = "last_execution_date")
    private LocalDateTime lastExecutionDate;

    @Column(name = "last_transaction_id", length = 36)
    private String lastTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ScheduledBill() {}

    public ScheduledBill(User user, Biller biller, String consumerNumber, BigDecimal amount,
                         BillFrequency frequency, LocalDate dueDate, LocalDateTime nextExecutionDate) {
        this.user = user;
        this.biller = biller;
        this.consumerNumber = consumerNumber;
        this.amount = amount;
        this.frequency = frequency;
        this.dueDate = dueDate;
        this.nextExecutionDate = nextExecutionDate;
        this.status = ScheduledBillStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public void advanceNextExecution() {
        this.lastExecutionDate = LocalDateTime.now();
        if (this.frequency == BillFrequency.ONE_TIME) {
            this.status = ScheduledBillStatus.COMPLETED;
        } else if (this.frequency == BillFrequency.DAILY) {
            this.nextExecutionDate = this.nextExecutionDate.plusDays(1);
        } else if (this.frequency == BillFrequency.WEEKLY) {
            this.nextExecutionDate = this.nextExecutionDate.plusWeeks(1);
        } else if (this.frequency == BillFrequency.MONTHLY) {
            this.nextExecutionDate = this.nextExecutionDate.plusMonths(1);
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Biller getBiller() { return biller; }
    public void setBiller(Biller biller) { this.biller = biller; }

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
