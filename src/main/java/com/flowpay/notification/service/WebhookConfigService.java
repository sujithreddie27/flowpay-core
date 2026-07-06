package com.flowpay.notification.service;

import com.flowpay.notification.dto.*;
import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WebhookConfigService {

    WebhookConfigResponse createWebhookConfig(CreateWebhookConfigRequest request);

    WebhookConfigResponse updateWebhookConfig(UUID configId, UpdateWebhookConfigRequest request);

    WebhookConfigResponse getWebhookConfig(UUID configId);

    List<WebhookConfigResponse> getWebhookConfigsByMerchant(UUID merchantId);

    void deleteWebhookConfig(UUID configId);

    String regenerateSecret(UUID configId);

    Page<WebhookDeliveryResponse> getDeliveries(UUID configId, Pageable pageable);

    void triggerWebhooks(UUID merchantId, WebhookEventType eventType,
                         UUID transactionId, java.util.Map<String, Object> payload);
}
