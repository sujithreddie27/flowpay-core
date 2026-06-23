package com.flowpay.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${flowpay.kafka.topics.payment-initiated}")
    private String paymentInitiatedTopic;

    @Value("${flowpay.kafka.topics.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${flowpay.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${flowpay.kafka.topics.audit-events}")
    private String auditEventsTopic;

    @Bean
    public NewTopic paymentInitiatedTopic() {
        return TopicBuilder.name(paymentInitiatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(paymentCompletedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(paymentFailedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name(auditEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Dead Letter Topics
    @Bean
    public NewTopic paymentInitiatedDlt() {
        return TopicBuilder.name(paymentInitiatedTopic + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCompletedDlt() {
        return TopicBuilder.name(paymentCompletedTopic + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentFailedDlt() {
        return TopicBuilder.name(paymentFailedTopic + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsDlt() {
        return TopicBuilder.name(auditEventsTopic + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
