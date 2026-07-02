package com.flowpay.integration;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.service.TransactionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaPaymentEventFlowTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("flowpay_kafka_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private Consumer<String, PaymentEvent> consumer;
    private User senderUser;
    private User receiverUser;
    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        senderUser = userRepository.save(User.builder()
                .email("kafka-sender@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Kafka")
                .lastName("Sender")
                .phone("+7777777777")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        receiverUser = userRepository.save(User.builder()
                .email("kafka-receiver@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Kafka")
                .lastName("Receiver")
                .phone("+8888888888")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        senderAccount = accountRepository.save(Account.builder()
                .user(senderUser)
                .accountNumber("KAFKA-SEND-001")
                .balance(new BigDecimal("10000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());

        receiverAccount = accountRepository.save(Account.builder()
                .user(receiverUser)
                .accountNumber("KAFKA-RECV-001")
                .balance(new BigDecimal("5000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());

        // Set up consumer for verifying published events
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-event-flow-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentEvent.class.getName());

        DefaultKafkaConsumerFactory<String, PaymentEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new JsonDeserializer<>(PaymentEvent.class, false));
        consumer = consumerFactory.createConsumer();
        consumer.subscribe(Arrays.asList("payment-initiated", "payment-completed", "payment-failed"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Nested
    @DisplayName("Payment Event Publishing")
    class PaymentEventPublishing {

        @Test
        @DisplayName("should publish payment-initiated and payment-completed events on successful payment")
        void shouldPublishEventsOnSuccessfulPayment() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("250.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Kafka event flow test")
                    .idempotencyKey("kafka-flow-" + UUID.randomUUID())
                    .build();

            TransactionResponse response = transactionService.initiatePayment(request);
            assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            // Poll for events
            List<PaymentEvent> events = new ArrayList<>();
            ConsumerRecords<String, PaymentEvent> records = consumer.poll(Duration.ofSeconds(10));
            records.forEach(record -> events.add(record.value()));

            // Should have at least initiated + completed events
            assertThat(events).hasSizeGreaterThanOrEqualTo(2);

            boolean hasInitiated = events.stream()
                    .anyMatch(e -> e.getEventType() == PaymentEventType.PAYMENT_INITIATED);
            boolean hasCompleted = events.stream()
                    .anyMatch(e -> e.getEventType() == PaymentEventType.PAYMENT_COMPLETED);

            assertThat(hasInitiated).isTrue();
            assertThat(hasCompleted).isTrue();

            // Verify event data
            PaymentEvent completedEvent = events.stream()
                    .filter(e -> e.getEventType() == PaymentEventType.PAYMENT_COMPLETED)
                    .findFirst()
                    .orElseThrow();

            assertThat(completedEvent.getTransactionId()).isEqualTo(response.getId());
            assertThat(completedEvent.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
            assertThat(completedEvent.getCurrency()).isEqualTo("USD");
            assertThat(completedEvent.getSenderId()).isEqualTo(senderUser.getId());
            assertThat(completedEvent.getReceiverId()).isEqualTo(receiverUser.getId());
        }

        @Test
        @DisplayName("should publish payment-failed event on failed payment")
        void shouldPublishFailedEventOnFailure() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("99999.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Should fail - insufficient funds")
                    .idempotencyKey("kafka-fail-" + UUID.randomUUID())
                    .build();

            try {
                transactionService.initiatePayment(request);
            } catch (Exception ignored) {
            }

            // Poll for events
            List<PaymentEvent> events = new ArrayList<>();
            ConsumerRecords<String, PaymentEvent> records = consumer.poll(Duration.ofSeconds(10));
            records.forEach(record -> events.add(record.value()));

            boolean hasFailed = events.stream()
                    .anyMatch(e -> e.getEventType() == PaymentEventType.PAYMENT_FAILED);
            assertThat(hasFailed).isTrue();

            PaymentEvent failedEvent = events.stream()
                    .filter(e -> e.getEventType() == PaymentEventType.PAYMENT_FAILED)
                    .findFirst()
                    .orElseThrow();

            assertThat(failedEvent.getFailureReason()).isNotBlank();
            assertThat(failedEvent.getTransactionStatus()).isEqualTo(TransactionStatus.FAILED);
        }
    }
}
