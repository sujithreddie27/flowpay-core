package com.flowpay.notification.service;

import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.entity.WebhookDelivery;
import com.flowpay.notification.enums.WebhookEventType;

import java.util.Map;
import java.util.UUID;

public interface WebhookDeliveryService {

    void deliverWebhook(WebhookConfig config, WebhookEventType eventType,
                        UUID transactionId, Map<String, Object> payload);

    void retryFailedDeliveries();

    void retryDelivery(UUID deliveryId);
}
