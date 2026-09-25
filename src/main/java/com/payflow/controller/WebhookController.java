package com.payflow.controller;

import com.payflow.common.ApiResponse;
import com.payflow.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Asynchronous payment status webhooks from external payment gateway")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/gateway")
    @Operation(summary = "Receive async payment status webhook from external gateway",
            description = "Protected via HMAC-SHA256 signature in X-Gateway-Signature header")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveGatewayWebhook(
            @Parameter(name = "X-Gateway-Signature", in = ParameterIn.HEADER, required = true, description = "HMAC-SHA256 signature of request body")
            @RequestHeader(value = "X-Gateway-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        webhookService.processWebhook(rawPayload, signature);
        return ResponseEntity.ok(ApiResponse.success("Webhook processed successfully", Map.of("received", true)));
    }
}
