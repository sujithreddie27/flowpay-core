package com.flowpay.kafka.consumer;

import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.AuditAction;
import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.repository.AuditLogRepository;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.service.EventDeduplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private EventDeduplicationService deduplicationService;

    @InjectMocks
    private AuditEventConsumer auditEventConsumer;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

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
                .referenceId("TXN-AUDIT-001")
                .eventType(PaymentEventType.PAYMENT_INITIATED)
                .transactionStatus(TransactionStatus.PENDING)
                .transactionType(TransactionType.TRANSFER)
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .senderAccountId(UUID.randomUUID())
                .receiverAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .fee(new BigDecimal("0.50"))
                .timestamp(OffsetDateTime.now())
                .build();
    }

    private ConsumerRecord<String, PaymentEvent> createConsumerRecord(PaymentEvent event) {
        return new ConsumerRecord<>("audit-events", 0, 0L, transactionId.toString(), event);
    }

    @Nested
    @DisplayName("Handle Audit Event")
    class HandleAuditEvent {

        @Test
        @DisplayName("Should create audit log for payment initiated")
        void shouldCreateAuditLogForPaymentInitiated() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();

            assertThat(savedLog.getEntityType()).isEqualTo("TRANSACTION");
            assertThat(savedLog.getEntityId()).isEqualTo(transactionId);
            assertThat(savedLog.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(savedLog.getNewValue()).containsEntry("referenceId", "TXN-AUDIT-001");
            assertThat(savedLog.getNewValue()).containsEntry("currency", "USD");
        }

        @Test
        @DisplayName("Should create audit log for payment completed")
        void shouldCreateAuditLogForPaymentCompleted() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_COMPLETED);
            paymentEvent.setTransactionStatus(TransactionStatus.COMPLETED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getAction()).isEqualTo(AuditAction.TRANSFER);
        }

        @Test
        @DisplayName("Should create audit log for payment failed")
        void shouldCreateAuditLogForPaymentFailed() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_FAILED);
            paymentEvent.setTransactionStatus(TransactionStatus.FAILED);
            paymentEvent.setFailureReason("Timeout");
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            AuditLog savedLog = auditLogCaptor.getValue();
            assertThat(savedLog.getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
            assertThat(savedLog.getNewValue()).containsEntry("failureReason", "Timeout");
        }

        @Test
        @DisplayName("Should create audit log for payment reversed")
        void shouldCreateAuditLogForPaymentReversed() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_REVERSED);
            paymentEvent.setTransactionStatus(TransactionStatus.REVERSED);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            assertThat(auditLogCaptor.getValue().getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
        }

        @Test
        @DisplayName("Should create audit log for payment retry")
        void shouldCreateAuditLogForPaymentRetry() {
            paymentEvent.setEventType(PaymentEventType.PAYMENT_RETRY);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            assertThat(auditLogCaptor.getValue().getAction()).isEqualTo(AuditAction.UPDATE);
        }

        @Test
        @DisplayName("Should skip duplicate audit event")
        void shouldSkipDuplicateAuditEvent() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(true);

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository, never()).save(any());
            verify(deduplicationService, never()).markProcessed(any(), any(), any());
        }

        @Test
        @DisplayName("Should rethrow exception on failure")
        void shouldRethrowOnFailure() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThatThrownBy(() -> auditEventConsumer.handleAuditEvent(record))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");

            verify(deduplicationService, never()).markProcessed(any(), any(), any());
        }

        @Test
        @DisplayName("Should include fee in audit log when present")
        void shouldIncludeFeeInAuditLog() {
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            assertThat(auditLogCaptor.getValue().getNewValue()).containsKey("fee");
        }

        @Test
        @DisplayName("Should not include failure reason when null")
        void shouldNotIncludeFailureReasonWhenNull() {
            paymentEvent.setFailureReason(null);
            ConsumerRecord<String, PaymentEvent> record = createConsumerRecord(paymentEvent);
            when(deduplicationService.isDuplicate(eventId, "audit-group")).thenReturn(false);
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditEventConsumer.handleAuditEvent(record);

            verify(auditLogRepository).save(auditLogCaptor.capture());
            assertThat(auditLogCaptor.getValue().getNewValue()).doesNotContainKey("failureReason");
        }
    }
}
