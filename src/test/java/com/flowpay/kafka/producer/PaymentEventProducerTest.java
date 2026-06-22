package com.flowpay.kafka.producer;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.*;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventProducerTest {

    @Mock
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @InjectMocks
    private PaymentEventProducer paymentEventProducer;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<PaymentEvent> eventCaptor;

    private Transaction transaction;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentEventProducer, "paymentInitiatedTopic", "payment-initiated");
        ReflectionTestUtils.setField(paymentEventProducer, "paymentCompletedTopic", "payment-completed");
        ReflectionTestUtils.setField(paymentEventProducer, "paymentFailedTopic", "payment-failed");
        ReflectionTestUtils.setField(paymentEventProducer, "auditEventsTopic", "audit-events");

        transactionId = UUID.randomUUID();
        UUID senderUserId = UUID.randomUUID();
        UUID receiverUserId = UUID.randomUUID();
        UUID senderAccountId = UUID.randomUUID();
        UUID receiverAccountId = UUID.randomUUID();

        User sender = User.builder()
                .email("sender@test.com")
                .firstName("Sender")
                .lastName("User")
                .passwordHash("hash")
                .build();
        sender.setId(senderUserId);

        User receiver = User.builder()
                .email("receiver@test.com")
                .firstName("Receiver")
                .lastName("User")
                .passwordHash("hash")
                .build();
        receiver.setId(receiverUserId);

        Account senderAccount = Account.builder()
                .user(sender)
                .accountNumber("1000000001")
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.00"))
                .build();
        senderAccount.setId(senderAccountId);

        Account receiverAccount = Account.builder()
                .user(receiver)
                .accountNumber("1000000002")
                .balance(new BigDecimal("2000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.00"))
                .build();
        receiverAccount.setId(receiverAccountId);

        transaction = Transaction.builder()
                .referenceId("TXN-ABC123")
                .sender(sender)
                .receiver(receiver)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .fee(BigDecimal.ZERO)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .build();
        transaction.setId(transactionId);
    }

    @SuppressWarnings("unchecked")
    private void mockKafkaSend() {
        CompletableFuture<SendResult<String, PaymentEvent>> future = new CompletableFuture<>();
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("test", 0), 0, 0, 0L, 0, 0);
        SendResult<String, PaymentEvent> result = new SendResult<>(
                new ProducerRecord<>("test", "key", null), metadata);
        future.complete(result);
        when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEvent.class))).thenReturn(future);
    }

    @Nested
    @DisplayName("publishPaymentInitiated")
    class PublishPaymentInitiated {

        @Test
        @DisplayName("should send event to payment-initiated topic")
        void shouldSendToCorrectTopic() {
            mockKafkaSend();

            paymentEventProducer.publishPaymentInitiated(transaction);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("payment-initiated");
            assertThat(keyCaptor.getValue()).isEqualTo(transactionId.toString());

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_INITIATED);
            assertThat(event.getTransactionId()).isEqualTo(transactionId);
            assertThat(event.getReferenceId()).isEqualTo("TXN-ABC123");
            assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(event.getCurrency()).isEqualTo("USD");
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("publishPaymentCompleted")
    class PublishPaymentCompleted {

        @Test
        @DisplayName("should send event to payment-completed topic")
        void shouldSendToCorrectTopic() {
            mockKafkaSend();
            transaction.setStatus(TransactionStatus.COMPLETED);

            paymentEventProducer.publishPaymentCompleted(transaction);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("payment-completed");

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_COMPLETED);
            assertThat(event.getTransactionStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("publishPaymentFailed")
    class PublishPaymentFailed {

        @Test
        @DisplayName("should send event to payment-failed topic with failure reason")
        void shouldSendToCorrectTopicWithFailureReason() {
            mockKafkaSend();
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Insufficient funds");

            paymentEventProducer.publishPaymentFailed(transaction);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("payment-failed");

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_FAILED);
            assertThat(event.getFailureReason()).isEqualTo("Insufficient funds");
        }
    }

    @Nested
    @DisplayName("publishPaymentCancelled")
    class PublishPaymentCancelled {

        @Test
        @DisplayName("should send cancellation event to payment-failed topic")
        void shouldSendToPaymentFailedTopic() {
            mockKafkaSend();
            transaction.setStatus(TransactionStatus.CANCELLED);

            paymentEventProducer.publishPaymentCancelled(transaction);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("payment-failed");

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_CANCELLED);
        }
    }

    @Nested
    @DisplayName("publishPaymentReversed")
    class PublishPaymentReversed {

        @Test
        @DisplayName("should send reversal event to payment-failed topic")
        void shouldSendToPaymentFailedTopic() {
            mockKafkaSend();
            transaction.setStatus(TransactionStatus.REVERSED);
            transaction.setFailureReason("Customer dispute");

            paymentEventProducer.publishPaymentReversed(transaction);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("payment-failed");

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_REVERSED);
            assertThat(event.getFailureReason()).isEqualTo("Customer dispute");
        }
    }

    @Nested
    @DisplayName("publishPaymentRetry")
    class PublishPaymentRetry {

        @Test
        @DisplayName("should send retry event to payment-initiated topic")
        void shouldSendToPaymentInitiatedTopic() {
            mockKafkaSend();

            paymentEventProducer.publishPaymentRetry(transaction);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("payment-initiated");

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_RETRY);
        }
    }

    @Nested
    @DisplayName("publishAuditEvent")
    class PublishAuditEvent {

        @Test
        @DisplayName("should send audit event to audit-events topic")
        void shouldSendToAuditEventsTopic() {
            mockKafkaSend();

            paymentEventProducer.publishAuditEvent(transaction, PaymentEventType.PAYMENT_INITIATED);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
            assertThat(topicCaptor.getValue()).isEqualTo("audit-events");

            PaymentEvent event = eventCaptor.getValue();
            assertThat(event.getEventType()).isEqualTo(PaymentEventType.PAYMENT_INITIATED);
        }
    }

    @Nested
    @DisplayName("Event building")
    class EventBuilding {

        @Test
        @DisplayName("should populate all event fields from transaction")
        void shouldPopulateAllFields() {
            mockKafkaSend();

            paymentEventProducer.publishPaymentInitiated(transaction);

            verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());
            PaymentEvent event = eventCaptor.getValue();

            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getTransactionId()).isEqualTo(transactionId);
            assertThat(event.getReferenceId()).isEqualTo("TXN-ABC123");
            assertThat(event.getTransactionStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(event.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
            assertThat(event.getSenderId()).isEqualTo(transaction.getSender().getId());
            assertThat(event.getReceiverId()).isEqualTo(transaction.getReceiver().getId());
            assertThat(event.getSenderAccountId()).isEqualTo(transaction.getSenderAccount().getId());
            assertThat(event.getReceiverAccountId()).isEqualTo(transaction.getReceiverAccount().getId());
            assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(event.getCurrency()).isEqualTo("USD");
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should use transaction ID as Kafka message key")
        void shouldUseTransactionIdAsKey() {
            mockKafkaSend();

            paymentEventProducer.publishPaymentCompleted(transaction);

            verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), any(PaymentEvent.class));
            assertThat(keyCaptor.getValue()).isEqualTo(transactionId.toString());
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle send failure gracefully without throwing")
        void shouldHandleSendFailureGracefully() {
            CompletableFuture<SendResult<String, PaymentEvent>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Kafka unavailable"));
            when(kafkaTemplate.send(anyString(), anyString(), any(PaymentEvent.class))).thenReturn(future);

            // Should not throw
            paymentEventProducer.publishPaymentInitiated(transaction);

            verify(kafkaTemplate).send(anyString(), anyString(), any(PaymentEvent.class));
        }
    }
}
