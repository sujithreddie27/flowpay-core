package com.flowpay.notification.service;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.notification.dto.NotificationRequest;
import com.flowpay.notification.dto.NotificationResponse;
import com.flowpay.notification.entity.Notification;
import com.flowpay.notification.entity.NotificationPreference;
import com.flowpay.notification.enums.NotificationStatus;
import com.flowpay.notification.enums.NotificationType;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.mapper.NotificationMapper;
import com.flowpay.notification.repository.NotificationPreferenceRepository;
import com.flowpay.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final EmailNotificationService emailNotificationService;
    private final WebhookConfigService webhookConfigService;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request) {
        log.info("Sending notification: userId={}, type={}, subject={}",
                request.getUserId(), request.getType(), request.getSubject());

        NotificationPreference preference = preferenceRepository.findByUserId(request.getUserId())
                .orElse(NotificationPreference.builder()
                        .userId(request.getUserId())
                        .emailEnabled(true)
                        .build());

        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .subject(request.getSubject())
                .content(request.getContent())
                .recipientEmail(request.getRecipientEmail())
                .transactionId(request.getTransactionId())
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        try {
            if (request.getType() == NotificationType.EMAIL && preference.isEmailEnabled()) {
                emailNotificationService.sendPaymentNotificationEmail(request);
                notification.markSent();
            } else if (request.getType() == NotificationType.WEBHOOK && preference.isWebhookEnabled()) {
                notification.markSent();
            } else {
                notification.markSent();
            }
        } catch (Exception e) {
            log.error("Failed to send notification: userId={}, error={}",
                    request.getUserId(), e.getMessage(), e);
            notification.markFailed(e.getMessage());
        }

        notification = notificationRepository.save(notification);
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional
    public void sendPaymentCompletedNotification(UUID transactionId, UUID userId) {
        log.info("Sending payment completed notification: transactionId={}, userId={}",
                transactionId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.EMAIL)
                .subject("Payment Completed Successfully")
                .content(String.format(
                        "Dear %s,\n\nYour payment (Transaction: %s) has been completed successfully.\n\nThank you for using FlowPay.",
                        user.getFirstName(), transactionId))
                .recipientEmail(user.getEmail())
                .transactionId(transactionId)
                .build();

        sendNotification(request);
    }

    @Override
    @Transactional
    public void sendPaymentFailedNotification(UUID transactionId, UUID userId, String reason) {
        log.info("Sending payment failed notification: transactionId={}, userId={}",
                transactionId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        NotificationRequest request = NotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.EMAIL)
                .subject("Payment Failed")
                .content(String.format(
                        "Dear %s,\n\nYour payment (Transaction: %s) has failed.\nReason: %s\n\nPlease try again or contact support.",
                        user.getFirstName(), transactionId, reason))
                .recipientEmail(user.getEmail())
                .transactionId(transactionId)
                .build();

        sendNotification(request);
    }

    @Override
    @Transactional
    public void sendWebhookNotification(UUID transactionId, UUID merchantId, String eventType) {
        log.info("Triggering webhook notification: transactionId={}, merchantId={}, event={}",
                transactionId, merchantId, eventType);

        WebhookEventType webhookEventType;
        try {
            webhookEventType = WebhookEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid webhook event type: {}", eventType);
            return;
        }

        Map<String, Object> payload = Map.of(
                "event", eventType,
                "transactionId", transactionId.toString(),
                "merchantId", merchantId.toString(),
                "timestamp", java.time.OffsetDateTime.now().toString()
        );

        webhookConfigService.triggerWebhooks(merchantId, webhookEventType, transactionId, payload);
    }
}
