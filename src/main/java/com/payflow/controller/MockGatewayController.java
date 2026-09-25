package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.enums.PaymentStatus;
import com.payflow.service.MockPaymentGatewayService;
import com.payflow.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mock-gateway")
@Tag(name = "Mock Gateway Simulator", description = "Endpoints for inspecting mock gateway state and simulating webhooks")
public class MockGatewayController {

    private final MockPaymentGatewayService gatewayService;
    private final WebhookService webhookService;

    public MockGatewayController(MockPaymentGatewayService gatewayService, WebhookService webhookService) {
        this.gatewayService = gatewayService;
        this.webhookService = webhookService;
    }

    @GetMapping("/registry")
    @Operation(summary = "Inspect all transactions recorded in the mock gateway settlement ledger")
    public ResponseEntity<ApiResponse<Map<String, MockPaymentGatewayService.GatewayTransactionRecord>>> getSettlementRegistry() {
        return ResponseEntity.ok(ApiResponse.success(gatewayService.getAllSettledRecords()));
    }

    @PostMapping("/simulate-webhook")
    @Operation(summary = "Simulate an asynchronous webhook callback from the gateway with valid HMAC signature")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerSimulatedWebhook(
            @RequestParam String transactionRef,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "SUCCESS") PaymentStatus status) {

        MockPaymentGatewayService.WebhookSimulationResult simulation =
                gatewayService.createSimulatedWebhook(transactionRef, amount, status);

        // Deliver to webhook service internally
        webhookService.processWebhook(simulation.getRawJson(), simulation.getSignature());

        return ResponseEntity.ok(ApiResponse.success("Simulated webhook dispatched and processed", Map.of(
                "eventId", simulation.getPayload().getEventId(),
                "transactionRef", transactionRef,
                "status", status,
                "signature", simulation.getSignature()
        )));
    }
}
