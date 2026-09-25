package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.config.KafkaConfig;
import com.payflow.dto.PaymentNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumerService.class);

    private final ObjectMapper objectMapper;

    public NotificationConsumerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes payment notification events from Kafka topic and simulates SMS/Email delivery.
     */
    @KafkaListener(
            topics = KafkaConfig.PAYMENT_NOTIFICATIONS_TOPIC,
            groupId = "payflow-notification-group"
    )
    public void consumePaymentNotification(Object message) {
        try {
            PaymentNotificationEvent event;
            if (message instanceof PaymentNotificationEvent) {
                event = (PaymentNotificationEvent) message;
            } else if (message instanceof String) {
                event = objectMapper.readValue((String) message, PaymentNotificationEvent.class);
            } else {
                event = objectMapper.convertValue(message, PaymentNotificationEvent.class);
            }

            log.info("================================================================================");
            log.info(">>> KAFKA NOTIFICATION WORKER CONSUMED EVENT: {}", event.getEventId());
            log.info(">>> [RECIPIENT]: Email={}, Phone={}", event.getUserEmail(), event.getUserPhone());
            log.info(">>> [TXN REF]:   {}", event.getTransactionRef());
            log.info(">>> [EVENT TYPE]:{}", event.getEventType());
            log.info(">>> [DETAILS]:   Paid {} {} to {} for Consumer: {}",
                    event.getCurrency(), event.getAmount(), event.getBillerName(), event.getConsumerNumber());
            log.info(">>> [STATUS]:    {} (Reason: {})", event.getStatus(), event.getReason() != null ? event.getReason() : "None");
            log.info("================================================================================");

        } catch (Exception e) {
            log.error("Error processing Kafka payment notification: {}", e.getMessage(), e);
        }
    }
}
