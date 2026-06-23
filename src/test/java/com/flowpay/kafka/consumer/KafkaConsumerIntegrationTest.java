package com.flowpay.kafka.consumer;

import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.repository.AuditLogRepository;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.entity.ProcessedEvent;
import com.flowpay.kafka.repository.ProcessedEventRepository;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.TransactionRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "payment-initiated",
                "payment-completed",
                "payment-failed",
                "audit-events",
                "payment-initiated.DLT",
                "payment-completed.DLT",
                "payment-failed.DLT",
                "audit-events.DLT"
        },
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "port=0"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaConsumerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private ProcessedEventRepository processedEventRepository;

    private Producer<String, PaymentEvent> producer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        DefaultKafkaProducerFactory<String, PaymentEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), new JsonSerializer<>());
        producer = producerFactory.createProducer();

        when(processedEventRepository.existsByEventIdAndConsumerGroup(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
    }

    private PaymentEvent buildTestEvent(PaymentEventType eventType, TransactionStatus status) {
        return PaymentEvent.builder()
                .eventId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .referenceId("TXN-INT-" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(eventType)
                .transactionStatus(status)
                .transactionType(TransactionType.TRANSFER)
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .senderAccountId(UUID.randomUUID())
                .receiverAccountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .fee(new BigDecimal("1.00"))
                .timestamp(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Payment Completed Consumer Integration")
    class PaymentCompletedIntegration {

        @Test
        @DisplayName("Should consume payment-completed event and update transaction")
        void shouldConsumePaymentCompletedEvent() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_COMPLETED, TransactionStatus.COMPLETED);

            Transaction transaction = Transaction.builder()
                    .referenceId(event.getReferenceId())
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(event.getTransactionId());

            when(transactionRepository.findById(event.getTransactionId())).thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

            producer.send(new ProducerRecord<>("payment-completed",
                    event.getTransactionId().toString(), event));
            producer.flush();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(transactionRepository, atLeastOnce()).findById(event.getTransactionId()));
        }
    }

    @Nested
    @DisplayName("Audit Event Consumer Integration")
    class AuditEventIntegration {

        @Test
        @DisplayName("Should consume audit event and create audit log")
        void shouldConsumeAuditEventAndCreateAuditLog() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_INITIATED, TransactionStatus.PENDING);

            producer.send(new ProducerRecord<>("audit-events",
                    event.getTransactionId().toString(), event));
            producer.flush();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class)));
        }
    }

    @Nested
    @DisplayName("Event Deduplication Integration")
    class DeduplicationIntegration {

        @Test
        @DisplayName("Should skip processing for duplicate events")
        void shouldSkipDuplicateEvents() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_COMPLETED, TransactionStatus.COMPLETED);

            // Mark as already processed
            when(processedEventRepository.existsByEventIdAndConsumerGroup(
                    eq(event.getEventId()), any())).thenReturn(true);

            producer.send(new ProducerRecord<>("payment-completed",
                    event.getTransactionId().toString(), event));
            producer.flush();

            // Wait a bit then verify transaction repo was NOT called
            await().pollDelay(2, TimeUnit.SECONDS)
                    .atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() ->
                            verify(transactionRepository, never()).findById(event.getTransactionId()));
        }
    }

    @Nested
    @DisplayName("Payment Failed Consumer Integration")
    class PaymentFailedIntegration {

        @Test
        @DisplayName("Should consume payment-failed event")
        void shouldConsumePaymentFailedEvent() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_FAILED, TransactionStatus.FAILED);
            event.setFailureReason("Insufficient funds");

            Transaction transaction = Transaction.builder()
                    .referenceId(event.getReferenceId())
                    .status(TransactionStatus.FAILED)
                    .failureReason("Insufficient funds")
                    .build();
            transaction.setId(event.getTransactionId());

            when(transactionRepository.findById(event.getTransactionId())).thenReturn(Optional.of(transaction));

            producer.send(new ProducerRecord<>("payment-failed",
                    event.getTransactionId().toString(), event));
            producer.flush();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(transactionRepository, atLeastOnce()).findById(event.getTransactionId()));
        }
    }

    @Nested
    @DisplayName("Dead Letter Topic Integration")
    class DeadLetterTopicIntegration {

        @Test
        @DisplayName("Should send to DLT after max retries exhausted")
        void shouldSendToDltAfterMaxRetries() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_COMPLETED, TransactionStatus.COMPLETED);

            // Simulate persistent failure
            when(transactionRepository.findById(event.getTransactionId()))
                    .thenThrow(new RuntimeException("Persistent DB error"));

            producer.send(new ProducerRecord<>("payment-completed",
                    event.getTransactionId().toString(), event));
            producer.flush();

            // Verify the consumer attempted processing (retried)
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(transactionRepository, atLeast(2)).findById(event.getTransactionId()));

            // Verify message lands in DLT
            Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                    "dlt-verification-group", "true", embeddedKafkaBroker);
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentEvent.class.getName());
            consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

            DefaultKafkaConsumerFactory<String, PaymentEvent> consumerFactory =
                    new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(),
                            new JsonDeserializer<>(PaymentEvent.class, false));

            Consumer<String, PaymentEvent> dltConsumer = consumerFactory.createConsumer();
            dltConsumer.subscribe(Collections.singletonList("payment-completed.DLT"));

            ConsumerRecords<String, PaymentEvent> records =
                    dltConsumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThan(0);
            ConsumerRecord<String, PaymentEvent> dltRecord = records.iterator().next();
            assertThat(dltRecord.value().getTransactionId()).isEqualTo(event.getTransactionId());

            dltConsumer.close();
        }
    }
}
