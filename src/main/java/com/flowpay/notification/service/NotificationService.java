package com.flowpay.notification.service;

import com.flowpay.notification.dto.NotificationRequest;
import com.flowpay.notification.dto.NotificationResponse;

import java.util.UUID;

public interface NotificationService {

    NotificationResponse sendNotification(NotificationRequest request);

    void sendPaymentCompletedNotification(UUID transactionId, UUID userId);

    void sendPaymentFailedNotification(UUID transactionId, UUID userId, String reason);

    void sendWebhookNotification(UUID transactionId, UUID merchantId, String eventType);
}
