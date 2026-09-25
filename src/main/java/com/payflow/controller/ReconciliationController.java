package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.dto.ReconciliationSummaryDto;
import com.payflow.entity.ReconciliationRecord;
import com.payflow.repository.ReconciliationRecordRepository;
import com.payflow.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Cross-check pending transactions against external gateway settlements")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationRecordRepository reconciliationRecordRepository;

    public ReconciliationController(ReconciliationService reconciliationService,
                                  ReconciliationRecordRepository reconciliationRecordRepository) {
        this.reconciliationService = reconciliationService;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
    }

    @PostMapping("/run")
    @Operation(summary = "Trigger on-demand reconciliation job")
    public ResponseEntity<ApiResponse<ReconciliationSummaryDto>> triggerReconciliation() {
        ReconciliationSummaryDto summary = reconciliationService.runReconciliation();
        return ResponseEntity.ok(ApiResponse.success("Reconciliation completed", summary));
    }

    @GetMapping("/records")
    @Operation(summary = "Get historical reconciliation discrepancy records")
    public ResponseEntity<ApiResponse<List<ReconciliationRecord>>> getDiscrepancyRecords(
            @RequestParam(required = false, defaultValue = "false") boolean unresolvedOnly) {
        List<ReconciliationRecord> records = unresolvedOnly ?
                reconciliationRecordRepository.findByResolvedOrderByCreatedAtDesc(false) :
                reconciliationRecordRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(records));
    }
}
