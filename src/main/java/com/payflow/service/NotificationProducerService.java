package com.payflow.service;

import com.payflow.config.KafkaConfig;
import com.payflow.dto.PaymentNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationProducerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationProducerService(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Emits a payment notification event asynchronously to Kafka topic.
     * Includes resilient fallback if Kafka broker is unavailable or not configured.
     */
    public void publishPaymentNotification(PaymentNotificationEvent event) {
        log.info("Publishing payment notification event: type={}, txnRef={}, user={}",
                event.getEventType(), event.getTransactionRef(), event.getUserEmail());

        if (kafkaTemplate == null) {
            simulateDirectNotification(event);
            return;
        }

        try {
            CompletableFuture<?> future = kafkaTemplate.send(
                    KafkaConfig.PAYMENT_NOTIFICATIONS_TOPIC,
                    event.getTransactionRef(),
                    event
            );

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish event to Kafka for txnRef={}: {}", event.getTransactionRef(), ex.getMessage());
                    simulateDirectNotification(event);
                } else {
                    log.info("Successfully published Kafka event for txnRef={}", event.getTransactionRef());
                }
            });
        } catch (Exception ex) {
            log.warn("Kafka broker unreachable, falling back to direct notification simulation: {}", ex.getMessage());
            simulateDirectNotification(event);
        }
    }

    public void simulateDirectNotification(PaymentNotificationEvent event) {
        log.info("[NOTIFICATION DISPATCHED] Channel: SMS/Email, User: {}, Event: {}, Amount: {} {}, Status: {}, Message: {}",
                event.getUserEmail(), event.getEventType(), event.getCurrency(), event.getAmount(),
                event.getStatus(), event.getReason() != null ? event.getReason() : "Success");
    }
}
