package com.flowpay.kafka.consumer;

import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.service.EventDeduplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private EventDeduplicationService deduplicationService;

    @InjectMocks
    private NotificationEventConsumer notificationEventConsumer;

    private PaymentEvent paymentEvent;
    private UUID transactionId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        paymentEvent = PaymentEvent.builder()
                .eventId(eventId)
                .transactionId(transactionId)
                .referenceId("TXN-NOTIF-001")
                .eventType(PaymentEventType.PAYMENT_COMPLETED)
                .transactionStatus(TransactionStatus.COMPLETED)
                .transactionType(TransactionType.TRANSFER)
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .senderAccountId(UUID.randomUUID())
                .receiverAccountId(UUID.randomUUID())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .fee(new BigDecimal("2.00"))
                .timestamp(OffsetDateTime.now())
                .build();
    }

    private ConsumerRecord<String, PaymentEvent> createConsumerRecord(String topic, PaymentEvent event) {
        return new ConsumerRecord<>(topic, 0, 0L, transactionId.toString(), event);
    }

    @Nested
    @DisplayName("Payment Completed Notifications")
    class PaymentCompletedNotifications {

        @Test
        @DisplayName("Should send notification for payment completed")
        void shouldSendNotificationForPaymentCompleted() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);

            notificationEventConsumer.handlePaymentCompletedNotification(record);

            verify(deduplicationService).markProcessed(eventId, "payment-completed", "notification-group");
        }

        @Test
        @DisplayName("Should skip duplicate notification")
        void shouldSkipDuplicateNotification() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(true);

            notificationEventConsumer.handlePaymentCompletedNotification(record);

            verify(deduplicationService, never()).markProcessed(any(), any(), any());
        }

        @Test
        @DisplayName("Should rethrow exception on failure")
        void shouldRethrowOnFailure() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);
            doThrow(new RuntimeException("DB error")).when(deduplicationService)
                    .markProcessed(any(), any(), any());

            assertThatThrownBy(() -> notificationEventConsumer.handlePaymentCompletedNotification(record))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Payment Failed Notifications")
    class PaymentFailedNotifications {

        @Test
        @DisplayName("Should send notification for payment failed")
        void shouldSendNotificationForPaymentFailed() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_FAILED);
            paymentEvent.setFailureReason("Insufficient funds");
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);

            notificationEventConsumer.handlePaymentFailedNotification(record);

            verify(deduplicationService).markProcessed(eventId, "payment-failed", "notification-group");
        }

        @Test
        @DisplayName("Should send notification for payment reversed")
        void shouldSendNotificationForPaymentReversed() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_REVERSED);
            paymentEvent.setFailureReason("Fraud detected");
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);

            notificationEventConsumer.handlePaymentFailedNotification(record);

            verify(deduplicationService).markProcessed(eventId, "payment-failed", "notification-group");
        }

        @Test
        @DisplayName("Should send notification for payment cancelled")
        void shouldSendNotificationForPaymentCancelled() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_CANCELLED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);

            notificationEventConsumer.handlePaymentFailedNotification(record);

            verify(deduplicationService).markProcessed(eventId, "payment-failed", "notification-group");
        }

        @Test
        @DisplayName("Should skip duplicate failed notification")
        void shouldSkipDuplicateFailedNotification() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_FAILED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(true);

            notificationEventConsumer.handlePaymentFailedNotification(record);

            verify(deduplicationService, never()).markProcessed(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Payment Initiated Notifications")
    class PaymentInitiatedNotifications {

        @Test
        @DisplayName("Should send notification for payment initiated")
        void shouldSendNotificationForPaymentInitiated() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_INITIATED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-initiated", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);

            notificationEventConsumer.handlePaymentInitiatedNotification(record);

            verify(deduplicationService).markProcessed(eventId, "payment-initiated", "notification-group");
        }

        @Test
        @DisplayName("Should send notification for payment retry")
        void shouldSendNotificationForPaymentRetry() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_RETRY);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-initiated", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(false);

            notificationEventConsumer.handlePaymentInitiatedNotification(record);

            verify(deduplicationService).markProcessed(eventId, "payment-initiated", "notification-group");
        }

        @Test
        @DisplayName("Should skip duplicate initiated notification")
        void shouldSkipDuplicateInitiatedNotification() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_INITIATED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-initiated", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "notification-group")).thenReturn(true);

            notificationEventConsumer.handlePaymentInitiatedNotification(record);

            verify(deduplicationService, never()).markProcessed(any(), any(), any());
        }
    }
}
