package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.dto.BillerAccountDto;
import com.payflow.dto.BillerDto;
import com.payflow.dto.CreateBillerAccountRequest;
import com.payflow.dto.CreateBillerRequest;
import com.payflow.entity.User;
import com.payflow.enums.BillerCategory;
import com.payflow.service.AuthService;
import com.payflow.service.BillerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/billers")
@Tag(name = "Billers", description = "Biller catalog and saved user accounts")
public class BillerController {

    private final BillerService billerService;
    private final AuthService authService;

    public BillerController(BillerService billerService, AuthService authService) {
        this.billerService = billerService;
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Get list of all supported billers (optionally filtered by category)")
    public ResponseEntity<ApiResponse<List<BillerDto>>> getBillers(
            @RequestParam(required = false) BillerCategory category) {
        List<BillerDto> billers = category != null ?
                billerService.getBillersByCategory(category) :
                billerService.getAllBillers();
        return ResponseEntity.ok(ApiResponse.success(billers));
    }

    @GetMapping("/categories")
    @Operation(summary = "Get all available biller categories")
    public ResponseEntity<ApiResponse<List<BillerCategory>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(Arrays.asList(BillerCategory.values())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get biller details by ID")
    public ResponseEntity<ApiResponse<BillerDto>> getBillerById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(billerService.getBillerById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new biller provider (Admin)")
    public ResponseEntity<ApiResponse<BillerDto>> createBiller(@Valid @RequestBody CreateBillerRequest request) {
        BillerDto biller = billerService.createBiller(request);
        return new ResponseEntity<>(ApiResponse.success("Biller registered successfully", biller), HttpStatus.CREATED);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Get current user's saved biller accounts")
    public ResponseEntity<ApiResponse<List<BillerAccountDto>>> getMyBillerAccounts() {
        User user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(billerService.getUserBillerAccounts(user.getId())));
    }

    @PostMapping("/accounts")
    @Operation(summary = "Save a biller consumer account for quick payments")
    public ResponseEntity<ApiResponse<BillerAccountDto>> saveBillerAccount(
            @Valid @RequestBody CreateBillerAccountRequest request) {
        User user = authService.getCurrentUser();
        BillerAccountDto account = billerService.saveBillerAccount(user, request);
        return new ResponseEntity<>(ApiResponse.success("Account saved successfully", account), HttpStatus.CREATED);
    }
}
