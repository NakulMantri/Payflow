package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.entity.Biller;
import com.payflow.entity.PaymentTransaction;
import com.payflow.entity.User;
import com.payflow.enums.BillerCategory;
import com.payflow.enums.PaymentStatus;
import com.payflow.enums.Role;
import com.payflow.exception.InvalidSignatureException;
import com.payflow.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private MockPaymentGatewayService gatewayService;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private NotificationProducerService notificationProducer;

    @Mock
    private IdempotencyService idempotencyService;

    private WebhookService webhookService;
    private PaymentTransaction testTxn;
    private User testUser;
    private Biller testBiller;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                "secret_key_123",
                gatewayService,
                transactionRepository,
                paymentService,
                notificationProducer,
                idempotencyService,
                new ObjectMapper()
        );

        testUser = new User("wh@payflow.com", "hash", "Webhook User", "12345", Role.ROLE_USER);
        testUser.setId(6L);

        testBiller = new Biller("TATAPLAY", "Tata Play", BillerCategory.DTH, ".*", "Sub ID", BigDecimal.ZERO, "ESC_TP");

        testTxn = new PaymentTransaction();
        testTxn.setId("txn-wh-1");
        testTxn.setTransactionRef("TXN_WH_001");
        testTxn.setIdempotencyKey("idemp_wh_1");
        testTxn.setUser(testUser);
        testTxn.setBiller(testBiller);
        testTxn.setAmount(new BigDecimal("350.00"));
        testTxn.setStatus(PaymentStatus.PENDING_GATEWAY);
    }

    @Test
    @DisplayName("Should reject webhook if HMAC signature is invalid")
    void testWebhook_invalidSignature_throwsException() {
        String rawJson = "{\"transactionRef\":\"TXN_WH_001\",\"status\":\"SUCCESS\"}";
        when(gatewayService.calculateHmacSha256(rawJson, "secret_key_123")).thenReturn("valid_sig");

        assertThatThrownBy(() -> webhookService.processWebhook(rawJson, "invalid_sig"))
                .isInstanceOf(InvalidSignatureException.class)
                .hasMessageContaining("Invalid webhook HMAC-SHA256 signature");

        verify(transactionRepository, never()).findByTransactionRef(anyString());
    }

    @Test
    @DisplayName("Should accept valid webhook, finalize transaction and dispatch notification")
    void testWebhook_validSignature_success() {
        String rawJson = "{\"transactionRef\":\"TXN_WH_001\",\"status\":\"SUCCESS\",\"gatewayReference\":\"GW_WH_99\",\"responseCode\":\"00\",\"message\":\"Settled\"}";
        when(gatewayService.calculateHmacSha256(rawJson, "secret_key_123")).thenReturn("valid_sig");
        when(transactionRepository.findByTransactionRef("TXN_WH_001")).thenReturn(Optional.of(testTxn));

        when(paymentService.finalizeSuccess("txn-wh-1", "GW_WH_99", "00")).thenReturn(testTxn);

        webhookService.processWebhook(rawJson, "valid_sig");

        verify(paymentService).finalizeSuccess("txn-wh-1", "GW_WH_99", "00");
        verify(notificationProducer).publishPaymentNotification(any());
    }
}
