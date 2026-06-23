package com.flowpay.kafka.consumer;

import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.service.EventDeduplicationService;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final String CONSUMER_GROUP = "payment-processing-group";

    private final TransactionRepository transactionRepository;
    private final EventDeduplicationService deduplicationService;

    @KafkaListener(
            topics = "${flowpay.kafka.topics.payment-completed}",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentCompleted(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Received payment-completed event: eventId={}, transactionId={}",
                event.getEventId(), event.getTransactionId());

        if (deduplicationService.isDuplicate(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate event detected, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            processPaymentCompleted(event);
            deduplicationService.markProcessed(event.getEventId(), record.topic(), CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Error processing payment-completed event: eventId={}, transactionId={}, error={}",
                    event.getEventId(), event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${flowpay.kafka.topics.payment-failed}",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentFailed(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Received payment-failed event: eventId={}, transactionId={}, eventType={}",
                event.getEventId(), event.getTransactionId(), event.getEventType());

        if (deduplicationService.isDuplicate(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate event detected, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            processPaymentFailed(event);
            deduplicationService.markProcessed(event.getEventId(), record.topic(), CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Error processing payment-failed event: eventId={}, transactionId={}, error={}",
                    event.getEventId(), event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }

    private void processPaymentCompleted(PaymentEvent event) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(event.getTransactionId());

        if (transactionOpt.isEmpty()) {
            log.warn("Transaction not found for completed event: transactionId={}", event.getTransactionId());
            return;
        }

        Transaction transaction = transactionOpt.get();
        log.info("Payment completed processing: transactionId={}, amount={}, currency={}, sender={}, receiver={}",
                transaction.getId(), event.getAmount(), event.getCurrency(),
                event.getSenderId(), event.getReceiverId());

        // Post-completion processing: update any derived state, trigger downstream actions
        if (transaction.getProcessedAt() == null) {
            transaction.setProcessedAt(event.getTimestamp());
            transactionRepository.save(transaction);
        }
    }

    private void processPaymentFailed(PaymentEvent event) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(event.getTransactionId());

        if (transactionOpt.isEmpty()) {
            log.warn("Transaction not found for failed event: transactionId={}", event.getTransactionId());
            return;
        }

        Transaction transaction = transactionOpt.get();
        log.info("Payment failed processing: transactionId={}, eventType={}, reason={}",
                transaction.getId(), event.getEventType(), event.getFailureReason());

        // Handle based on event type
        if (event.getEventType() == PaymentEventType.PAYMENT_REVERSED) {
            log.info("Payment reversal processed: transactionId={}, reason={}",
                    transaction.getId(), event.getFailureReason());
        } else if (event.getEventType() == PaymentEventType.PAYMENT_CANCELLED) {
            log.info("Payment cancellation processed: transactionId={}", transaction.getId());
        }
    }
}
