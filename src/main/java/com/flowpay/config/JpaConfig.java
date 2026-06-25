package com.flowpay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration for the application.
 * Enables JPA auditing, repositories, and transaction management.
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {
    "com.flowpay.auth.repository",
    "com.flowpay.transaction.repository",
    "com.flowpay.payments.repository",
    "com.flowpay.common.repository",
    "com.flowpay.kafka.repository"
})
@EnableTransactionManagement
public class JpaConfig {
    // JPA auditing and repository configuration
}
