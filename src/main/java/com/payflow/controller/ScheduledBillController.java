package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.dto.ScheduleBillRequest;
import com.payflow.dto.ScheduledBillDto;
import com.payflow.entity.User;
import com.payflow.service.AuthService;
import com.payflow.service.ScheduledBillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bills")
@Tag(name = "Scheduled & Recurring Bills", description = "Schedule one-time or recurring automated bill payments")
public class ScheduledBillController {

    private final ScheduledBillService scheduledBillService;
    private final AuthService authService;

    public ScheduledBillController(ScheduledBillService scheduledBillService, AuthService authService) {
        this.scheduledBillService = scheduledBillService;
        this.authService = authService;
    }

    @PostMapping("/schedule")
    @Operation(summary = "Schedule a one-time or recurring bill payment")
    public ResponseEntity<ApiResponse<ScheduledBillDto>> scheduleBill(
            @Valid @RequestBody ScheduleBillRequest request) {
        User user = authService.getCurrentUser();
        ScheduledBillDto dto = scheduledBillService.scheduleBill(user, request);
        return new ResponseEntity<>(ApiResponse.success("Bill payment scheduled successfully", dto), HttpStatus.CREATED);
    }

    @GetMapping("/scheduled")
    @Operation(summary = "List all scheduled bills for current user")
    public ResponseEntity<ApiResponse<List<ScheduledBillDto>>> getMyScheduledBills() {
        User user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(scheduledBillService.getUserScheduledBills(user.getId())));
    }

    @DeleteMapping("/scheduled/{id}")
    @Operation(summary = "Cancel a scheduled bill")
    public ResponseEntity<ApiResponse<String>> cancelScheduledBill(@PathVariable Long id) {
        User user = authService.getCurrentUser();
        scheduledBillService.cancelScheduledBill(user, id);
        return ResponseEntity.ok(ApiResponse.success("Scheduled bill cancelled successfully", null));
    }
}
