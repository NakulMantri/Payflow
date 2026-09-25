package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.dto.LedgerEntryDto;
import com.payflow.dto.WalletResponse;
import com.payflow.dto.WalletTopUpRequest;
import com.payflow.entity.User;
import com.payflow.enums.LedgerAccountType;
import com.payflow.service.AuthService;
import com.payflow.service.LedgerService;
import com.payflow.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallet & Ledger", description = "Wallet balance, top-up, and double-entry ledger audits")
public class WalletController {

    private final WalletService walletService;
    private final AuthService authService;
    private final LedgerService ledgerService;

    public WalletController(WalletService walletService, AuthService authService, LedgerService ledgerService) {
        this.walletService = walletService;
        this.authService = authService;
        this.ledgerService = ledgerService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user's wallet balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet() {
        User user = authService.getCurrentUser();
        WalletResponse response = walletService.getWalletByUserId(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/top-up")
    @Operation(summary = "Top up wallet funds (atomic double-entry record)")
    public ResponseEntity<ApiResponse<WalletResponse>> topUpWallet(@Valid @RequestBody WalletTopUpRequest request) {
        User user = authService.getCurrentUser();
        WalletResponse response = walletService.topUp(user, request);
        return ResponseEntity.ok(ApiResponse.success("Wallet credited successfully", response));
    }

    @GetMapping("/ledger")
    @Operation(summary = "Get immutable double-entry ledger records for current user's wallet")
    public ResponseEntity<ApiResponse<List<LedgerEntryDto>>> getWalletLedger() {
        User user = authService.getCurrentUser();
        WalletResponse wallet = walletService.getWalletByUserId(user.getId());
        List<LedgerEntryDto> entries = ledgerService.getEntriesByAccount(wallet.getId().toString(), LedgerAccountType.USER_WALLET);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }
}
