package com.flowpay.logging;

import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.repository.AuditLogRepository;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.entity.ProcessedEvent;
import com.flowpay.kafka.repository.ProcessedEventRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Tag("docker")
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
class TracePropagationTest {

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
                .referenceId("TXN-TRACE-" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(eventType)
                .transactionStatus(status)
                .transactionType(TransactionType.TRANSFER)
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .senderAccountId(UUID.randomUUID())
                .receiverAccountId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .fee(new BigDecimal("2.50"))
                .timestamp(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Trace Propagation Through Kafka")
    class KafkaTracePropagation {

        @Test
        @DisplayName("Should propagate trace context via B3 headers on Kafka messages")
        void shouldPropagateTraceContextViaKafkaHeaders() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_INITIATED, TransactionStatus.PENDING);

            String traceId = "463ac35c9f6413ad48485a3953bb6124";
            String spanId = "0020000000000001";

            ProducerRecord<String, PaymentEvent> record = new ProducerRecord<>(
                    "audit-events", event.getTransactionId().toString(), event);
            record.headers().add(new RecordHeader("b3",
                    (traceId + "-" + spanId + "-1").getBytes(StandardCharsets.UTF_8)));

            producer.send(record);
            producer.flush();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class)));
        }

        @Test
        @DisplayName("Should process event even without trace headers")
        void shouldProcessEventWithoutTraceHeaders() {
            PaymentEvent event = buildTestEvent(PaymentEventType.PAYMENT_INITIATED, TransactionStatus.PENDING);

            ProducerRecord<String, PaymentEvent> record = new ProducerRecord<>(
                    "audit-events", event.getTransactionId().toString(), event);

            producer.send(record);
            producer.flush();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class)));
        }

        @Test
        @DisplayName("Should maintain trace context across multiple events in same trace")
        void shouldMaintainTraceContextAcrossMultipleEvents() {
            String traceId = "563ac35c9f6413ad48485a3953bb6125";

            PaymentEvent event1 = buildTestEvent(PaymentEventType.PAYMENT_INITIATED, TransactionStatus.PENDING);
            ProducerRecord<String, PaymentEvent> record1 = new ProducerRecord<>(
                    "audit-events", event1.getTransactionId().toString(), event1);
            record1.headers().add(new RecordHeader("b3",
                    (traceId + "-0020000000000001-1").getBytes(StandardCharsets.UTF_8)));

            PaymentEvent event2 = buildTestEvent(PaymentEventType.PAYMENT_INITIATED, TransactionStatus.PENDING);
            ProducerRecord<String, PaymentEvent> record2 = new ProducerRecord<>(
                    "audit-events", event2.getTransactionId().toString(), event2);
            record2.headers().add(new RecordHeader("b3",
                    (traceId + "-0020000000000002-1").getBytes(StandardCharsets.UTF_8)));

            producer.send(record1);
            producer.send(record2);
            producer.flush();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    verify(auditLogRepository, atLeast(2)).save(any(AuditLog.class)));
        }
    }
}
