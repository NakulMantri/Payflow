package com.payflow.entity;

import com.payflow.enums.DiscrepancyType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_records", indexes = {
    @Index(name = "idx_recon_batch", columnList = "batch_id"),
    @Index(name = "idx_recon_txn", columnList = "transaction_id")
})
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "ledger_status", nullable = false, length = 50)
    private String ledgerStatus;

    @Column(name = "gateway_status", nullable = false, length = 50)
    private String gatewayStatus;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 50)
    private DiscrepancyType discrepancyType;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "resolution_notes", length = 500)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ReconciliationRecord() {}

    public ReconciliationRecord(String batchId, String transactionId, String ledgerStatus,
                                String gatewayStatus, BigDecimal amount, DiscrepancyType discrepancyType,
                                String resolutionNotes) {
        this.batchId = batchId;
        this.transactionId = transactionId;
        this.ledgerStatus = ledgerStatus;
        this.gatewayStatus = gatewayStatus;
        this.amount = amount;
        this.discrepancyType = discrepancyType;
        this.resolutionNotes = resolutionNotes;
        this.resolved = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getLedgerStatus() { return ledgerStatus; }
    public void setLedgerStatus(String ledgerStatus) { this.ledgerStatus = ledgerStatus; }

    public String getGatewayStatus() { return gatewayStatus; }
    public void setGatewayStatus(String gatewayStatus) { this.gatewayStatus = gatewayStatus; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public DiscrepancyType getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(DiscrepancyType discrepancyType) { this.discrepancyType = discrepancyType; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
