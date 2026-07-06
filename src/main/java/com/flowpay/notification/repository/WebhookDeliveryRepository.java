package com.flowpay.notification.repository;

import com.flowpay.notification.entity.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByWebhookConfigId(UUID webhookConfigId, Pageable pageable);

    List<WebhookDelivery> findByTransactionId(UUID transactionId);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.successful = false " +
            "AND d.attemptCount < d.maxAttempts " +
            "AND d.nextRetryAt <= :now")
    List<WebhookDelivery> findPendingRetries(@Param("now") OffsetDateTime now);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.webhookConfig.id = :configId " +
            "AND d.successful = false AND d.attemptCount >= d.maxAttempts")
    List<WebhookDelivery> findFailedDeliveries(@Param("configId") UUID configId);
}
