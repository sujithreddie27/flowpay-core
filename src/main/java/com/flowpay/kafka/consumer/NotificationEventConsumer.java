package com.flowpay.kafka.consumer;

import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.service.EventDeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final String CONSUMER_GROUP = "notification-group";

    private final EventDeduplicationService deduplicationService;

    @KafkaListener(
            topics = "${flowpay.kafka.topics.payment-completed}",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompletedNotification(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Notification consumer received payment-completed: eventId={}, transactionId={}",
                event.getEventId(), event.getTransactionId());

        if (deduplicationService.isDuplicate(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate notification event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            sendPaymentCompletedNotification(event);
            deduplicationService.markProcessed(event.getEventId(), record.topic(), CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Failed to send payment completed notification: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${flowpay.kafka.topics.payment-failed}",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailedNotification(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Notification consumer received payment-failed: eventId={}, transactionId={}, type={}",
                event.getEventId(), event.getTransactionId(), event.getEventType());

        if (deduplicationService.isDuplicate(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate notification event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            sendPaymentFailedNotification(event);
            deduplicationService.markProcessed(event.getEventId(), record.topic(), CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Failed to send payment failed notification: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${flowpay.kafka.topics.payment-initiated}",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentInitiatedNotification(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Notification consumer received payment-initiated: eventId={}, transactionId={}",
                event.getEventId(), event.getTransactionId());

        if (deduplicationService.isDuplicate(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate notification event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            sendPaymentInitiatedNotification(event);
            deduplicationService.markProcessed(event.getEventId(), record.topic(), CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Failed to send payment initiated notification: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    private void sendPaymentCompletedNotification(PaymentEvent event) {
        // Notify sender: payment sent successfully
        log.info("Sending payment success notification to sender: userId={}, amount={} {}, transactionId={}",
                event.getSenderId(), event.getAmount(), event.getCurrency(), event.getTransactionId());

        // Notify receiver: payment received
        log.info("Sending payment received notification to receiver: userId={}, amount={} {}, transactionId={}",
                event.getReceiverId(), event.getAmount(), event.getCurrency(), event.getTransactionId());
    }

    private void sendPaymentFailedNotification(PaymentEvent event) {
        if (event.getEventType() == PaymentEventType.PAYMENT_REVERSED) {
            log.info("Sending reversal notification: senderId={}, receiverId={}, transactionId={}, reason={}",
                    event.getSenderId(), event.getReceiverId(), event.getTransactionId(), event.getFailureReason());
        } else if (event.getEventType() == PaymentEventType.PAYMENT_CANCELLED) {
            log.info("Sending cancellation notification: senderId={}, transactionId={}",
                    event.getSenderId(), event.getTransactionId());
        } else {
            log.info("Sending payment failure notification: senderId={}, transactionId={}, reason={}",
                    event.getSenderId(), event.getTransactionId(), event.getFailureReason());
        }
    }

    private void sendPaymentInitiatedNotification(PaymentEvent event) {
        if (event.getEventType() == PaymentEventType.PAYMENT_RETRY) {
            log.info("Sending payment retry notification: senderId={}, transactionId={}",
                    event.getSenderId(), event.getTransactionId());
        } else {
            log.info("Sending payment initiated notification: senderId={}, receiverId={}, amount={} {}",
                    event.getSenderId(), event.getReceiverId(), event.getAmount(), event.getCurrency());
        }
    }
}
