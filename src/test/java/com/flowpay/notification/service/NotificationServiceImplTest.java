package com.flowpay.notification.service;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.notification.dto.NotificationRequest;
import com.flowpay.notification.dto.NotificationResponse;
import com.flowpay.notification.entity.Notification;
import com.flowpay.notification.entity.NotificationPreference;
import com.flowpay.notification.enums.NotificationStatus;
import com.flowpay.notification.enums.NotificationType;
import com.flowpay.notification.mapper.NotificationMapper;
import com.flowpay.notification.repository.NotificationPreferenceRepository;
import com.flowpay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private WebhookConfigService webhookConfigService;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private UUID userId;
    private UUID transactionId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        user = User.builder()
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("hashed")
                .build();
        user.setId(userId);
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @Test
        @DisplayName("should send email notification when email is enabled")
        void shouldSendEmailNotification() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .subject("Payment Completed")
                    .content("Your payment was successful")
                    .recipientEmail("test@example.com")
                    .transactionId(transactionId)
                    .build();

            NotificationPreference preference = NotificationPreference.builder()
                    .userId(userId)
                    .emailEnabled(true)
                    .build();

            Notification savedNotification = Notification.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.SENT)
                    .subject("Payment Completed")
                    .content("Your payment was successful")
                    .recipientEmail("test@example.com")
                    .transactionId(transactionId)
                    .build();
            savedNotification.setId(UUID.randomUUID());

            NotificationResponse expectedResponse = NotificationResponse.builder()
                    .id(savedNotification.getId())
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.SENT)
                    .build();

            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(notificationMapper.toResponse(any(Notification.class))).thenReturn(expectedResponse);

            NotificationResponse response = notificationService.sendNotification(request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo(NotificationType.EMAIL);
            verify(emailNotificationService).sendPaymentNotificationEmail(request);
        }

        @Test
        @DisplayName("should use default preferences when user has no preferences")
        void shouldUseDefaultPreferencesWhenNoneExist() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .subject("Payment Completed")
                    .content("Your payment was successful")
                    .recipientEmail("test@example.com")
                    .build();

            Notification savedNotification = Notification.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.SENT)
                    .subject("Payment Completed")
                    .content("Your payment was successful")
                    .build();
            savedNotification.setId(UUID.randomUUID());

            NotificationResponse expectedResponse = NotificationResponse.builder()
                    .id(savedNotification.getId())
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.SENT)
                    .build();

            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(notificationMapper.toResponse(any(Notification.class))).thenReturn(expectedResponse);

            NotificationResponse response = notificationService.sendNotification(request);

            assertThat(response).isNotNull();
            verify(emailNotificationService).sendPaymentNotificationEmail(request);
        }

        @Test
        @DisplayName("should mark notification as failed when email sending fails")
        void shouldMarkNotificationAsFailed() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .subject("Payment Completed")
                    .content("Your payment was successful")
                    .recipientEmail("test@example.com")
                    .build();

            NotificationPreference preference = NotificationPreference.builder()
                    .userId(userId)
                    .emailEnabled(true)
                    .build();

            Notification savedNotification = Notification.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.FAILED)
                    .subject("Payment Completed")
                    .content("Your payment was successful")
                    .build();
            savedNotification.setId(UUID.randomUUID());

            NotificationResponse expectedResponse = NotificationResponse.builder()
                    .id(savedNotification.getId())
                    .status(NotificationStatus.FAILED)
                    .build();

            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            doThrow(new RuntimeException("SMTP error"))
                    .when(emailNotificationService).sendPaymentNotificationEmail(request);
            when(notificationMapper.toResponse(any(Notification.class))).thenReturn(expectedResponse);

            NotificationResponse response = notificationService.sendNotification(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("sendPaymentCompletedNotification")
    class SendPaymentCompletedNotification {

        @Test
        @DisplayName("should send payment completed notification to user")
        void shouldSendPaymentCompletedNotification() {
            NotificationPreference preference = NotificationPreference.builder()
                    .userId(userId)
                    .emailEnabled(true)
                    .build();

            Notification savedNotification = Notification.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.SENT)
                    .subject("Payment Completed Successfully")
                    .content("test")
                    .build();
            savedNotification.setId(UUID.randomUUID());

            NotificationResponse expectedResponse = NotificationResponse.builder()
                    .id(savedNotification.getId())
                    .status(NotificationStatus.SENT)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(notificationMapper.toResponse(any(Notification.class))).thenReturn(expectedResponse);

            notificationService.sendPaymentCompletedNotification(transactionId, userId);

            verify(userRepository).findById(userId);
            verify(emailNotificationService).sendPaymentNotificationEmail(any(NotificationRequest.class));
        }
    }

    @Nested
    @DisplayName("sendPaymentFailedNotification")
    class SendPaymentFailedNotification {

        @Test
        @DisplayName("should send payment failed notification to user")
        void shouldSendPaymentFailedNotification() {
            NotificationPreference preference = NotificationPreference.builder()
                    .userId(userId)
                    .emailEnabled(true)
                    .build();

            Notification savedNotification = Notification.builder()
                    .userId(userId)
                    .type(NotificationType.EMAIL)
                    .status(NotificationStatus.SENT)
                    .subject("Payment Failed")
                    .content("test")
                    .build();
            savedNotification.setId(UUID.randomUUID());

            NotificationResponse expectedResponse = NotificationResponse.builder()
                    .id(savedNotification.getId())
                    .status(NotificationStatus.SENT)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
            when(notificationMapper.toResponse(any(Notification.class))).thenReturn(expectedResponse);

            notificationService.sendPaymentFailedNotification(transactionId, userId, "Insufficient funds");

            verify(userRepository).findById(userId);
            verify(emailNotificationService).sendPaymentNotificationEmail(any(NotificationRequest.class));
        }
    }

    @Nested
    @DisplayName("sendWebhookNotification")
    class SendWebhookNotification {

        @Test
        @DisplayName("should trigger webhooks for merchant")
        void shouldTriggerWebhooksForMerchant() {
            UUID merchantId = UUID.randomUUID();

            notificationService.sendWebhookNotification(transactionId, merchantId, "PAYMENT_COMPLETED");

            verify(webhookConfigService).triggerWebhooks(eq(merchantId), any(), eq(transactionId), any());
        }

        @Test
        @DisplayName("should not trigger webhooks for invalid event type")
        void shouldNotTriggerWebhooksForInvalidEventType() {
            UUID merchantId = UUID.randomUUID();

            notificationService.sendWebhookNotification(transactionId, merchantId, "INVALID_EVENT");

            verify(webhookConfigService, never()).triggerWebhooks(any(), any(), any(), any());
        }
    }
}
