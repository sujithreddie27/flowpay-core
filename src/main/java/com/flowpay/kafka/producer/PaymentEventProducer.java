package com.flowpay.kafka.producer;

import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${flowpay.kafka.topics.payment-initiated}")
    private String paymentInitiatedTopic;

    @Value("${flowpay.kafka.topics.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${flowpay.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    @Value("${flowpay.kafka.topics.audit-events}")
    private String auditEventsTopic;

    public void publishPaymentInitiated(Transaction transaction) {
        PaymentEvent event = buildEvent(transaction, PaymentEventType.PAYMENT_INITIATED);
        sendEvent(paymentInitiatedTopic, transaction.getId().toString(), event);
    }

    public void publishPaymentCompleted(Transaction transaction) {
        PaymentEvent event = buildEvent(transaction, PaymentEventType.PAYMENT_COMPLETED);
        sendEvent(paymentCompletedTopic, transaction.getId().toString(), event);
    }

    public void publishPaymentFailed(Transaction transaction) {
        PaymentEvent event = buildEvent(transaction, PaymentEventType.PAYMENT_FAILED);
        event.setFailureReason(transaction.getFailureReason());
        sendEvent(paymentFailedTopic, transaction.getId().toString(), event);
    }

    public void publishPaymentCancelled(Transaction transaction) {
        PaymentEvent event = buildEvent(transaction, PaymentEventType.PAYMENT_CANCELLED);
        sendEvent(paymentFailedTopic, transaction.getId().toString(), event);
    }

    public void publishPaymentReversed(Transaction transaction) {
        PaymentEvent event = buildEvent(transaction, PaymentEventType.PAYMENT_REVERSED);
        event.setFailureReason(transaction.getFailureReason());
        sendEvent(paymentFailedTopic, transaction.getId().toString(), event);
    }

    public void publishPaymentRetry(Transaction transaction) {
        PaymentEvent event = buildEvent(transaction, PaymentEventType.PAYMENT_RETRY);
        sendEvent(paymentInitiatedTopic, transaction.getId().toString(), event);
    }

    public void publishAuditEvent(Transaction transaction, PaymentEventType eventType) {
        PaymentEvent event = buildEvent(transaction, eventType);
        sendEvent(auditEventsTopic, transaction.getId().toString(), event);
    }

    private void sendEvent(String topic, String key, PaymentEvent event) {
        log.info("Publishing event: topic={}, key={}, eventType={}, transactionId={}",
                topic, key, event.getEventType(), event.getTransactionId());

        CompletableFuture<SendResult<String, PaymentEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event: topic={}, transactionId={}, error={}",
                        topic, event.getTransactionId(), ex.getMessage(), ex);
            } else {
                log.debug("Event published successfully: topic={}, partition={}, offset={}, transactionId={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event.getTransactionId());
            }
        });
    }

    private PaymentEvent buildEvent(Transaction transaction, PaymentEventType eventType) {
        return PaymentEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(transaction.getId())
                .referenceId(transaction.getReferenceId())
                .eventType(eventType)
                .transactionStatus(transaction.getStatus())
                .transactionType(transaction.getType())
                .senderId(transaction.getSender().getId())
                .receiverId(transaction.getReceiver().getId())
                .senderAccountId(transaction.getSenderAccount().getId())
                .receiverAccountId(transaction.getReceiverAccount().getId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .fee(transaction.getFee())
                .failureReason(transaction.getFailureReason())
                .timestamp(OffsetDateTime.now())
                .metadata(transaction.getMetadata())
                .build();
    }
}
