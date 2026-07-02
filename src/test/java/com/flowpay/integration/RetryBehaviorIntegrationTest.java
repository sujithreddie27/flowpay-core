package com.flowpay.integration;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.TransactionNotRetryableException;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryBehaviorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("flowpay_retry_test")
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

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

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
                .email("retry-sender@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Retry")
                .lastName("Sender")
                .phone("+3333333333")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        receiverUser = userRepository.save(User.builder()
                .email("retry-receiver@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Retry")
                .lastName("Receiver")
                .phone("+4444444444")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        senderAccount = accountRepository.save(Account.builder()
                .user(senderUser)
                .accountNumber("RETRY-SEND-001")
                .balance(new BigDecimal("10000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());

        receiverAccount = accountRepository.save(Account.builder()
                .user(receiverUser)
                .accountNumber("RETRY-RECV-001")
                .balance(new BigDecimal("5000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());
    }

    @Nested
    @DisplayName("Retry Failed Transactions")
    class RetryFailedTransactions {

        @Test
        @DisplayName("should successfully retry a failed transaction")
        void shouldRetryFailedTransaction() {
            // First, create a failed transaction manually
            Transaction failedTx = Transaction.builder()
                    .referenceId("TXN-RETRY-" + UUID.randomUUID().toString().substring(0, 8))
                    .idempotencyKey("retry-key-" + UUID.randomUUID())
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.FAILED)
                    .type(TransactionType.TRANSFER)
                    .description("Failed transaction for retry")
                    .failureReason("Temporary network error")
                    .retryCount(0)
                    .build();
            failedTx = transactionRepository.save(failedTx);

            // Retry the transaction
            TransactionResponse response = transactionService.retryTransaction(failedTx.getId());

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            // Verify balances updated
            Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            Account updatedReceiver = accountRepository.findById(receiverAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("9900.0000"));
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("5100.0000"));
        }

        @Test
        @DisplayName("should reject retry for exhausted transaction")
        void shouldRejectRetryWhenMaxAttemptsExhausted() {
            Transaction exhaustedTx = Transaction.builder()
                    .referenceId("TXN-EXHAUST-" + UUID.randomUUID().toString().substring(0, 8))
                    .idempotencyKey("exhaust-key-" + UUID.randomUUID())
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.FAILED)
                    .type(TransactionType.TRANSFER)
                    .description("Exhausted retries")
                    .retryCount(3)
                    .build();
            exhaustedTx = transactionRepository.save(exhaustedTx);

            UUID txId = exhaustedTx.getId();
            assertThatThrownBy(() -> transactionService.retryTransaction(txId))
                    .isInstanceOf(TransactionNotRetryableException.class);
        }

        @Test
        @DisplayName("should reject retry for completed transaction")
        void shouldRejectRetryForCompletedTransaction() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Completed - no retry")
                    .idempotencyKey("no-retry-" + UUID.randomUUID())
                    .build();

            TransactionResponse completed = transactionService.initiatePayment(request);

            assertThatThrownBy(() -> transactionService.retryTransaction(completed.getId()))
                    .isInstanceOf(TransactionNotRetryableException.class);
        }
    }

    @Nested
    @DisplayName("Retryable Transactions Query")
    class RetryableTransactionsQuery {

        @Test
        @DisplayName("should find retryable transactions")
        void shouldFindRetryableTransactions() {
            // Create a retryable failed transaction
            Transaction retryable = Transaction.builder()
                    .referenceId("TXN-FIND-RETRY-" + UUID.randomUUID().toString().substring(0, 8))
                    .idempotencyKey("find-retry-" + UUID.randomUUID())
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.FAILED)
                    .type(TransactionType.TRANSFER)
                    .retryCount(1)
                    .build();
            transactionRepository.save(retryable);

            // Create non-retryable (exhausted)
            Transaction exhausted = Transaction.builder()
                    .referenceId("TXN-NO-RETRY-" + UUID.randomUUID().toString().substring(0, 8))
                    .idempotencyKey("no-retry-q-" + UUID.randomUUID())
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("200.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.FAILED)
                    .type(TransactionType.TRANSFER)
                    .retryCount(3)
                    .build();
            transactionRepository.save(exhausted);

            List<TransactionResponse> retryables = transactionService.getRetryableTransactions();

            assertThat(retryables).hasSize(1);
            assertThat(retryables.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }

    @Nested
    @DisplayName("Stale Transaction Processing")
    class StaleTransactionProcessing {

        @Test
        @DisplayName("should process stale pending transactions")
        void shouldProcessStalePendingTransactions() {
            // Create a stale transaction by manually setting a past created_at
            Transaction staleTx = Transaction.builder()
                    .referenceId("TXN-STALE-" + UUID.randomUUID().toString().substring(0, 8))
                    .idempotencyKey("stale-key-" + UUID.randomUUID())
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("300.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.PENDING)
                    .type(TransactionType.TRANSFER)
                    .build();
            staleTx = transactionRepository.save(staleTx);

            // Update createdAt to be in the past
            transactionRepository.flush();
            transactionRepository.findById(staleTx.getId()).ifPresent(tx -> {
                tx.setCreatedAt(java.time.OffsetDateTime.now().minusHours(2));
                transactionRepository.save(tx);
            });

            int processed = transactionService.processStalePendingTransactions();

            assertThat(processed).isGreaterThanOrEqualTo(1);

            Transaction updated = transactionRepository.findById(staleTx.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(updated.getFailureReason()).contains("timed out");
        }
    }
}
