package com.flowpay.kafka.consumer;

import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.service.EventDeduplicationService;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.TransactionRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private EventDeduplicationService deduplicationService;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

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
                .referenceId("TXN-TEST-001")
                .eventType(PaymentEventType.PAYMENT_COMPLETED)
                .transactionStatus(TransactionStatus.COMPLETED)
                .transactionType(TransactionType.TRANSFER)
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .senderAccountId(UUID.randomUUID())
                .receiverAccountId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .fee(new BigDecimal("1.50"))
                .timestamp(OffsetDateTime.now())
                .build();
    }

    private ConsumerRecord<String, PaymentEvent> createConsumerRecord(String topic, PaymentEvent event) {
        return new ConsumerRecord<>(topic, 0, 0L, transactionId.toString(), event);
    }

    @Nested
    @DisplayName("Handle Payment Completed")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("Should process payment completed event successfully")
        void shouldProcessPaymentCompletedEvent() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST-001")
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(transactionId);

            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

            paymentEventConsumer.handlePaymentCompleted(record);

            verify(deduplicationService).markProcessed(eventId, "payment-completed", "payment-processing-group");
            verify(transactionRepository).findById(transactionId);
        }

        @Test
        @DisplayName("Should skip duplicate event")
        void shouldSkipDuplicateEvent() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(true);

            paymentEventConsumer.handlePaymentCompleted(record);

            verify(transactionRepository, never()).findById(any());
            verify(deduplicationService, never()).markProcessed(any(), any(), any());
        }

        @Test
        @DisplayName("Should handle transaction not found gracefully")
        void shouldHandleTransactionNotFound() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

            paymentEventConsumer.handlePaymentCompleted(record);

            verify(deduplicationService).markProcessed(eventId, "payment-completed", "payment-processing-group");
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should set processedAt if null")
        void shouldSetProcessedAtIfNull() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST-001")
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(transactionId);
            // processedAt is null

            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

            paymentEventConsumer.handlePaymentCompleted(record);

            verify(transactionRepository).save(transaction);
        }

        @Test
        @DisplayName("Should not overwrite existing processedAt")
        void shouldNotOverwriteProcessedAt() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST-001")
                    .status(TransactionStatus.COMPLETED)
                    .processedAt(OffsetDateTime.now().minusMinutes(5))
                    .build();
            transaction.setId(transactionId);

            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

            paymentEventConsumer.handlePaymentCompleted(record);

            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should rethrow exception on processing failure")
        void shouldRethrowOnProcessingFailure() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-completed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> paymentEventConsumer.handlePaymentCompleted(record))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }

    @Nested
    @DisplayName("Handle Payment Failed")
    class HandlePaymentFailed {

        @Test
        @DisplayName("Should process payment failed event")
        void shouldProcessPaymentFailedEvent() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_FAILED);
            paymentEvent.setTransactionStatus(TransactionStatus.FAILED);
            paymentEvent.setFailureReason("Insufficient funds");

            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST-001")
                    .status(TransactionStatus.FAILED)
                    .failureReason("Insufficient funds")
                    .build();
            transaction.setId(transactionId);

            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

            paymentEventConsumer.handlePaymentFailed(record);

            verify(deduplicationService).markProcessed(eventId, "payment-failed", "payment-processing-group");
        }

        @Test
        @DisplayName("Should process payment reversed event")
        void shouldProcessPaymentReversedEvent() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_REVERSED);
            paymentEvent.setTransactionStatus(TransactionStatus.REVERSED);
            paymentEvent.setFailureReason("Fraud detected");

            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST-001")
                    .status(TransactionStatus.REVERSED)
                    .build();
            transaction.setId(transactionId);

            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

            paymentEventConsumer.handlePaymentFailed(record);

            verify(deduplicationService).markProcessed(eventId, "payment-failed", "payment-processing-group");
        }

        @Test
        @DisplayName("Should process payment cancelled event")
        void shouldProcessPaymentCancelledEvent() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_CANCELLED);
            paymentEvent.setTransactionStatus(TransactionStatus.CANCELLED);

            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST-001")
                    .status(TransactionStatus.CANCELLED)
                    .build();
            transaction.setId(transactionId);

            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(false);
            when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

            paymentEventConsumer.handlePaymentFailed(record);

            verify(deduplicationService).markProcessed(eventId, "payment-failed", "payment-processing-group");
        }

        @Test
        @DisplayName("Should skip duplicate failed event")
        void shouldSkipDuplicateFailedEvent() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_FAILED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord("payment-failed", paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "payment-processing-group")).thenReturn(true);

            paymentEventConsumer.handlePaymentFailed(record);

            verify(transactionRepository, never()).findById(any());
        }
    }
}
