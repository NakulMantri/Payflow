package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayServiceTest {

    private MockPaymentGatewayService gatewayService;

    @BeforeEach
    void setUp() {
        gatewayService = new MockPaymentGatewayService(
                0.15,
                0,
                "test_webhook_secret_key_12345",
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Consumer number ending with 999 should reliably trigger simulated TIMEOUT")
    void testDeterministicTimeout() {
        MockPaymentGatewayService.GatewayResult result = gatewayService.processPayment(
                "TXN_DET_TIMEOUT",
                new BigDecimal("500.00"),
                "TATA_POWER",
                "999999999"
        );

        assertThat(result.getOutcome()).isEqualTo(MockPaymentGatewayService.GatewayOutcome.TIMEOUT);
        assertThat(result.getResponseCode()).isEqualTo("91");
    }

    @Test
    @DisplayName("Consumer number ending with 000 should reliably trigger simulated DECLINE")
    void testDeterministicDecline() {
        MockPaymentGatewayService.GatewayResult result = gatewayService.processPayment(
                "TXN_DET_DECLINE",
                new BigDecimal("100.00"),
                "AIRTEL",
                "9876543000"
        );

        assertThat(result.getOutcome()).isEqualTo(MockPaymentGatewayService.GatewayOutcome.FAILED);
        assertThat(result.getResponseCode()).isEqualTo("05");
    }

    @Test
    @DisplayName("Consumer number ending with 111 should reliably trigger simulated SUCCESS")
    void testDeterministicSuccess() {
        MockPaymentGatewayService.GatewayResult result = gatewayService.processPayment(
                "TXN_DET_SUCCESS",
                new BigDecimal("250.00"),
                "JIO",
                "9876543111"
        );

        assertThat(result.getOutcome()).isEqualTo(MockPaymentGatewayService.GatewayOutcome.SUCCESS);
        assertThat(result.getResponseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("Should generate valid HMAC-SHA256 signature for webhook payloads")
    void testHmacSha256Signature() {
        String payload = "{\"test\":\"data\"}";
        String sig1 = gatewayService.calculateHmacSha256(payload, "secret");
        String sig2 = gatewayService.calculateHmacSha256(payload, "secret");

        assertThat(sig1).isNotNull();
        assertThat(sig1).isEqualTo(sig2);
    }
}
