package com.payflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "payflow.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    public static final String PAYMENT_NOTIFICATIONS_TOPIC = "payflow.payment.notifications";
    public static final String PAYMENT_RETRY_TOPIC = "payflow.payment.retries";

    @Bean
    public NewTopic paymentNotificationsTopic() {
        return TopicBuilder.name(PAYMENT_NOTIFICATIONS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentRetryTopic() {
        return TopicBuilder.name(PAYMENT_RETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
