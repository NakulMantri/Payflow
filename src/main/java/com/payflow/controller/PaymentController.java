package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.dto.LedgerEntryDto;
import com.payflow.dto.PaymentInitiateRequest;
import com.payflow.dto.PaymentResponse;
import com.payflow.entity.User;
import com.payflow.service.AuthService;
import com.payflow.service.LedgerService;
import com.payflow.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment execution with idempotency, ledger, and status inspection")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthService authService;
    private final LedgerService ledgerService;

    public PaymentController(PaymentService paymentService, AuthService authService, LedgerService ledgerService) {
        this.paymentService = paymentService;
        this.authService = authService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @Operation(summary = "Execute idempotent bill payment or recharge",
            description = "Requires an Idempotency-Key header (UUID). Duplicate requests return cached results without double charging.")
    public ResponseEntity<ApiResponse<PaymentResponse>> payBill(
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true, description = "Unique UUID preventing double-charge")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentInitiateRequest request) {

        User user = authService.getCurrentUser();
        PaymentResponse response = paymentService.processPayment(user, idempotencyKey, request);

        HttpStatus status = switch (response.getStatus()) {
            case SUCCESS -> HttpStatus.OK;
            case RETRYING, PENDING_GATEWAY -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.OK;
        };

        return new ResponseEntity<>(ApiResponse.success("Payment processed: " + response.getStatus(), response), status);
    }

    @GetMapping
    @Operation(summary = "Get payment transaction history for current user")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getMyPayments() {
        User user = authService.getCurrentUser();
        List<PaymentResponse> transactions = paymentService.getUserTransactions(user.getId());
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction details by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable String id) {
        PaymentResponse response = paymentService.getTransactionById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/ledger")
    @Operation(summary = "Get double-entry ledger audit trail for a specific transaction")
    public ResponseEntity<ApiResponse<List<LedgerEntryDto>>> getTransactionLedger(@PathVariable String id) {
        List<LedgerEntryDto> entries = ledgerService.getEntriesByTransaction(id);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }
}
