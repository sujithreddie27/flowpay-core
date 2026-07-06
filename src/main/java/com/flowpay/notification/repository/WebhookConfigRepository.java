package com.flowpay.notification.repository;

import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.enums.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {

    List<WebhookConfig> findByMerchantId(UUID merchantId);

    List<WebhookConfig> findByMerchantIdAndStatus(UUID merchantId, WebhookStatus status);

    List<WebhookConfig> findByStatus(WebhookStatus status);
}
