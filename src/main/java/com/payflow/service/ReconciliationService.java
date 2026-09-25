package com.payflow.service;

import com.payflow.dto.ReconciliationSummaryDto;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.ReconciliationRecord;
import com.payflow.enums.DiscrepancyType;
import com.payflow.enums.PaymentStatus;
import com.payflow.repository.PaymentTransactionRepository;
import com.payflow.repository.ReconciliationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final PaymentTransactionRepository transactionRepository;
    private final ReconciliationRecordRepository reconciliationRepository;
    private final MockPaymentGatewayService gatewayService;
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public ReconciliationService(
            PaymentTransactionRepository transactionRepository,
            ReconciliationRecordRepository reconciliationRepository,
            MockPaymentGatewayService gatewayService,
            PaymentService paymentService,
            IdempotencyService idempotencyService) {
        this.transactionRepository = transactionRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.gatewayService = gatewayService;
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Scheduled automated reconciliation job.
     */
    @Scheduled(cron = "${payflow.reconciliation.cron:0 */5 * * * *}")
    public void runScheduledReconciliation() {
        log.info("Starting scheduled reconciliation execution...");
        runReconciliation();
    }

    /**
     * Executes reconciliation batch cross-checking pending/retrying internal transactions
     * against external gateway settlement registry.
     */
    @Transactional
    public ReconciliationSummaryDto runReconciliation() {
        String batchId = "RECON_" + System.currentTimeMillis();
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(5); // Transactions older than 5s

        List<PaymentTransaction> candidates = transactionRepository.findTransactionsForReconciliation(
                Arrays.asList(PaymentStatus.PENDING_GATEWAY, PaymentStatus.RETRYING),
                threshold
        );

        log.info("Reconciliation batch {}: found {} candidate transactions for verification", batchId, candidates.size());

        int totalChecked = 0;
        int matchedCount = 0;
        int discrepancyCount = 0;
        int autoResolvedCount = 0;
        List<ReconciliationSummaryDto.ReconciliationRecordDto> recordDtos = new ArrayList<>();

        for (PaymentTransaction txn : candidates) {
            totalChecked++;
            Optional<MockPaymentGatewayService.GatewayTransactionRecord> gwRecordOpt =
                    gatewayService.queryGatewayStatus(txn.getTransactionRef());

            if (gwRecordOpt.isPresent()) {
                MockPaymentGatewayService.GatewayTransactionRecord gwRecord = gwRecordOpt.get();

                // 1. Verify Amount
                if (txn.getAmount().compareTo(gwRecord.getAmount()) != 0) {
                    discrepancyCount++;
                    ReconciliationRecord rec = new ReconciliationRecord(
                            batchId,
                            txn.getId(),
                            txn.getStatus().name(),
                            gwRecord.getStatus().name(),
                            txn.getAmount(),
                            DiscrepancyType.AMOUNT_MISMATCH,
                            "Amount mismatch: DB=" + txn.getAmount() + " vs GW=" + gwRecord.getAmount()
                    );
                    rec.setResolved(false);
                    rec = reconciliationRepository.save(rec);
                    recordDtos.add(toRecordDto(rec));
                    continue;
                }

                // 2. Verify Status
                if (gwRecord.getStatus() == PaymentStatus.SUCCESS && txn.getStatus() != PaymentStatus.SUCCESS) {
                    discrepancyCount++;
                    autoResolvedCount++;
                    log.info("Reconciling txnRef={}: Gateway is SUCCESS, resolving internal status", txn.getTransactionRef());

                    PaymentTransaction resolved = paymentService.finalizeSuccess(
                            txn.getId(),
                            gwRecord.getGatewayReference(),
                            "00"
                    );
                    idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(resolved));

                    ReconciliationRecord rec = new ReconciliationRecord(
                            batchId,
                            txn.getId(),
                            txn.getStatus().name(),
                            gwRecord.getStatus().name(),
                            txn.getAmount(),
                            DiscrepancyType.STATUS_MISMATCH,
                            "Auto-reconciled: External gateway settled payment as SUCCESS"
                    );
                    rec.setResolved(true);
                    rec = reconciliationRepository.save(rec);
                    recordDtos.add(toRecordDto(rec));

                } else if (gwRecord.getStatus() == PaymentStatus.FAILED && txn.getStatus() != PaymentStatus.FAILED) {
                    discrepancyCount++;
                    autoResolvedCount++;
                    log.info("Reconciling txnRef={}: Gateway is FAILED, executing internal rollback & refund", txn.getTransactionRef());

                    PaymentTransaction refunded = paymentService.rollbackAndFail(
                            txn.getId(),
                            "Reconciliation identified gateway failure",
                            gwRecord.getGatewayReference(),
                            "05"
                    );
                    idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(refunded));

                    ReconciliationRecord rec = new ReconciliationRecord(
                            batchId,
                            txn.getId(),
                            txn.getStatus().name(),
                            gwRecord.getStatus().name(),
                            txn.getAmount(),
                            DiscrepancyType.STATUS_MISMATCH,
                            "Auto-reconciled: External gateway marked FAILED. Wallet refunded."
                    );
                    rec.setResolved(true);
                    rec = reconciliationRepository.save(rec);
                    recordDtos.add(toRecordDto(rec));
                } else {
                    matchedCount++;
                }

            } else {
                // Not found on gateway yet
                if (txn.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(2))) {
                    discrepancyCount++;
                    autoResolvedCount++;
                    log.warn("Reconciliation txnRef={} missing on gateway after 2m. Rolling back.", txn.getTransactionRef());

                    PaymentTransaction refunded = paymentService.rollbackAndFail(
                            txn.getId(),
                            "Reconciliation: Transaction missing on gateway switch",
                            null,
                            "99"
                    );
                    idempotencyService.recordSuccess(txn.getIdempotencyKey(), txn.getUser().getId(), paymentService.toDto(refunded));

                    ReconciliationRecord rec = new ReconciliationRecord(
                            batchId,
                            txn.getId(),
                            txn.getStatus().name(),
                            "UNKNOWN",
                            txn.getAmount(),
                            DiscrepancyType.MISSING_IN_GATEWAY,
                            "Auto-reconciled: Missing on external switch after timeout. Refunded to wallet."
                    );
                    rec.setResolved(true);
                    rec = reconciliationRepository.save(rec);
                    recordDtos.add(toRecordDto(rec));
                } else {
                    matchedCount++;
                }
            }
        }

        log.info("Reconciliation batch {} complete: checked={}, matched={}, discrepancies={}, resolved={}",
                batchId, totalChecked, matchedCount, discrepancyCount, autoResolvedCount);

        return new ReconciliationSummaryDto(
                batchId,
                totalChecked,
                matchedCount,
                discrepancyCount,
                autoResolvedCount,
                recordDtos,
                LocalDateTime.now()
        );
    }

    private ReconciliationSummaryDto.ReconciliationRecordDto toRecordDto(ReconciliationRecord r) {
        return new ReconciliationSummaryDto.ReconciliationRecordDto(
                r.getId(),
                r.getTransactionId(),
                r.getLedgerStatus(),
                r.getGatewayStatus(),
                r.getDiscrepancyType().name(),
                r.isResolved(),
                r.getResolutionNotes()
        );
    }
}
