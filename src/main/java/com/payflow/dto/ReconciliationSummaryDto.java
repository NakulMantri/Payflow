package com.payflow.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ReconciliationSummaryDto {
    private String batchId;
    private int totalChecked;
    private int matchedCount;
    private int discrepancyCount;
    private int autoResolvedCount;
    private List<ReconciliationRecordDto> records;
    private LocalDateTime executedAt;

    public ReconciliationSummaryDto() {}

    public ReconciliationSummaryDto(String batchId, int totalChecked, int matchedCount,
                                    int discrepancyCount, int autoResolvedCount,
                                    List<ReconciliationRecordDto> records, LocalDateTime executedAt) {
        this.batchId = batchId;
        this.totalChecked = totalChecked;
        this.matchedCount = matchedCount;
        this.discrepancyCount = discrepancyCount;
        this.autoResolvedCount = autoResolvedCount;
        this.records = records;
        this.executedAt = executedAt;
    }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public int getTotalChecked() { return totalChecked; }
    public void setTotalChecked(int totalChecked) { this.totalChecked = totalChecked; }

    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }

    public int getDiscrepancyCount() { return discrepancyCount; }
    public void setDiscrepancyCount(int discrepancyCount) { this.discrepancyCount = discrepancyCount; }

    public int getAutoResolvedCount() { return autoResolvedCount; }
    public void setAutoResolvedCount(int autoResolvedCount) { this.autoResolvedCount = autoResolvedCount; }

    public List<ReconciliationRecordDto> getRecords() { return records; }
    public void setRecords(List<ReconciliationRecordDto> records) { this.records = records; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public static class ReconciliationRecordDto {
        private Long id;
        private String transactionId;
        private String ledgerStatus;
        private String gatewayStatus;
        private String discrepancyType;
        private boolean resolved;
        private String resolutionNotes;

        public ReconciliationRecordDto() {}

        public ReconciliationRecordDto(Long id, String transactionId, String ledgerStatus,
                                       String gatewayStatus, String discrepancyType,
                                       boolean resolved, String resolutionNotes) {
            this.id = id;
            this.transactionId = transactionId;
            this.ledgerStatus = ledgerStatus;
            this.gatewayStatus = gatewayStatus;
            this.discrepancyType = discrepancyType;
            this.resolved = resolved;
            this.resolutionNotes = resolutionNotes;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

        public String getLedgerStatus() { return ledgerStatus; }
        public void setLedgerStatus(String ledgerStatus) { this.ledgerStatus = ledgerStatus; }

        public String getGatewayStatus() { return gatewayStatus; }
        public void setGatewayStatus(String gatewayStatus) { this.gatewayStatus = gatewayStatus; }

        public String getDiscrepancyType() { return discrepancyType; }
        public void setDiscrepancyType(String discrepancyType) { this.discrepancyType = discrepancyType; }

        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }

        public String getResolutionNotes() { return resolutionNotes; }
        public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    }
}
