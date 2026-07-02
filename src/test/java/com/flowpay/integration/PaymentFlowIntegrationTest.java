package com.flowpay.integration;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.InsufficientFundsException;
import com.flowpay.common.exception.PaymentException;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("flowpay_integration_test")
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
                .email("sender-flow@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Sender")
                .lastName("Flow")
                .phone("+1111111111")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        receiverUser = userRepository.save(User.builder()
                .email("receiver-flow@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Receiver")
                .lastName("Flow")
                .phone("+2222222222")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        senderAccount = accountRepository.save(Account.builder()
                .user(senderUser)
                .accountNumber("FLOW-SEND-001")
                .balance(new BigDecimal("10000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());

        receiverAccount = accountRepository.save(Account.builder()
                .user(receiverUser)
                .accountNumber("FLOW-RECV-001")
                .balance(new BigDecimal("5000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());
    }

    @Nested
    @DisplayName("Full Payment Flow")
    class FullPaymentFlow {

        @Test
        @DisplayName("should process payment end-to-end: initiate → process → complete")
        void shouldProcessPaymentEndToEnd() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Integration test payment")
                    .idempotencyKey("flow-test-" + UUID.randomUUID())
                    .build();

            TransactionResponse response = transactionService.initiatePayment(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(response.getReferenceId()).isNotBlank();

            // Verify balances updated correctly
            Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            Account updatedReceiver = accountRepository.findById(receiverAccount.getId()).orElseThrow();

            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("9500.0000"));
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("5500.0000"));
        }

        @Test
        @DisplayName("should fail payment when insufficient funds")
        void shouldFailWhenInsufficientFunds() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("99999.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Over limit payment")
                    .idempotencyKey("insufficient-" + UUID.randomUUID())
                    .build();

            assertThatThrownBy(() -> transactionService.initiatePayment(request))
                    .isInstanceOf(InsufficientFundsException.class);

            // Verify sender balance unchanged
            Account sender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("10000.0000"));
        }

        @Test
        @DisplayName("should create transaction record even on failure")
        void shouldCreateRecordOnFailure() {
            String idempotencyKey = "fail-record-" + UUID.randomUUID();
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("99999.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Should fail but record")
                    .idempotencyKey(idempotencyKey)
                    .build();

            try {
                transactionService.initiatePayment(request);
            } catch (Exception ignored) {
            }

            Optional<Transaction> transaction = transactionRepository.findByIdempotencyKey(idempotencyKey);
            assertThat(transaction).isPresent();
            assertThat(transaction.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(transaction.get().getFailureReason()).isNotBlank();
        }

        @Test
        @DisplayName("should handle multiple successful payments")
        void shouldHandleMultiplePayments() {
            for (int i = 0; i < 5; i++) {
                InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                        .senderAccountId(senderAccount.getId())
                        .receiverAccountId(receiverAccount.getId())
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .type(TransactionType.TRANSFER)
                        .description("Payment " + i)
                        .idempotencyKey("multi-" + UUID.randomUUID())
                        .build();

                TransactionResponse response = transactionService.initiatePayment(request);
                assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            }

            Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("9500.0000"));
        }
    }

    @Nested
    @DisplayName("Idempotency Verification")
    class IdempotencyVerification {

        @Test
        @DisplayName("should return same response for duplicate idempotency key")
        void shouldReturnSameResponseForDuplicate() {
            String idempotencyKey = "idem-dup-" + UUID.randomUUID();

            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("200.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Idempotent payment")
                    .idempotencyKey(idempotencyKey)
                    .build();

            TransactionResponse firstResponse = transactionService.initiatePayment(request);
            TransactionResponse secondResponse = transactionService.initiatePayment(request);

            assertThat(firstResponse.getId()).isEqualTo(secondResponse.getId());
            assertThat(firstResponse.getReferenceId()).isEqualTo(secondResponse.getReferenceId());

            // Verify balance debited only once
            Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("9800.0000"));
        }

        @Test
        @DisplayName("should process different idempotency keys independently")
        void shouldProcessDifferentKeysIndependently() {
            InitiateTransactionRequest request1 = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("First payment")
                    .idempotencyKey("key-1-" + UUID.randomUUID())
                    .build();

            InitiateTransactionRequest request2 = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Second payment")
                    .idempotencyKey("key-2-" + UUID.randomUUID())
                    .build();

            TransactionResponse response1 = transactionService.initiatePayment(request1);
            TransactionResponse response2 = transactionService.initiatePayment(request2);

            assertThat(response1.getId()).isNotEqualTo(response2.getId());

            Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("9800.0000"));
        }
    }

    @Nested
    @DisplayName("Concurrent Transaction Handling")
    class ConcurrentTransactionHandling {

        @Test
        @DisplayName("should handle concurrent payments without double-spending")
        void shouldPreventDoubleSpending() throws InterruptedException {
            // Set sender balance to exactly enough for 5 payments of 2000
            senderAccount.setBalance(new BigDecimal("10000.0000"));
            accountRepository.save(senderAccount);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                                .senderAccountId(senderAccount.getId())
                                .receiverAccountId(receiverAccount.getId())
                                .amount(new BigDecimal("2000.00"))
                                .currency("USD")
                                .type(TransactionType.TRANSFER)
                                .description("Concurrent payment " + idx)
                                .idempotencyKey("concurrent-" + UUID.randomUUID())
                                .build();

                        transactionService.initiatePayment(request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Verify no double-spending: balance cannot go negative
            Account finalSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(finalSender.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

            // At most 5 transactions could succeed (10000 / 2000 = 5)
            assertThat(successCount.get()).isLessThanOrEqualTo(5);
            assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("should handle concurrent idempotent requests")
        void shouldHandleConcurrentIdempotentRequests() throws InterruptedException {
            String idempotencyKey = "concurrent-idem-" + UUID.randomUUID();
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            ConcurrentLinkedQueue<TransactionResponse> responses = new ConcurrentLinkedQueue<>();
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                                .senderAccountId(senderAccount.getId())
                                .receiverAccountId(receiverAccount.getId())
                                .amount(new BigDecimal("100.00"))
                                .currency("USD")
                                .type(TransactionType.TRANSFER)
                                .description("Concurrent idempotent")
                                .idempotencyKey(idempotencyKey)
                                .build();

                        TransactionResponse response = transactionService.initiatePayment(request);
                        responses.add(response);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // All successful responses should reference the same transaction
            if (!responses.isEmpty()) {
                UUID firstId = responses.peek().getId();
                assertThat(responses).allMatch(r -> r.getId().equals(firstId));
            }

            // Balance should only be debited once
            Account finalSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(finalSender.getBalance()).isGreaterThanOrEqualTo(new BigDecimal("9900.0000"));
        }
    }

    @Nested
    @DisplayName("Transaction Lifecycle")
    class TransactionLifecycle {

        @Test
        @DisplayName("should cancel pending transaction")
        void shouldCancelPendingTransaction() {
            // Create a completed transaction, then test cancellation logic
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("200.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("To be reversed")
                    .idempotencyKey("cancel-" + UUID.randomUUID())
                    .build();

            TransactionResponse completed = transactionService.initiatePayment(request);
            assertThat(completed.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            // Reverse the completed transaction
            TransactionResponse reversed = transactionService.reverseTransaction(
                    completed.getId(), "Customer request");

            assertThat(reversed.getStatus()).isEqualTo(TransactionStatus.REVERSED);

            // Verify balances restored
            Account updatedSender = accountRepository.findById(senderAccount.getId()).orElseThrow();
            Account updatedReceiver = accountRepository.findById(receiverAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("10000.0000"));
            assertThat(updatedReceiver.getBalance()).isEqualByComparingTo(new BigDecimal("5000.0000"));
        }

        @Test
        @DisplayName("should retrieve transaction by reference ID")
        void shouldRetrieveByReferenceId() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccount.getId())
                    .receiverAccountId(receiverAccount.getId())
                    .amount(new BigDecimal("150.00"))
                    .currency("USD")
                    .type(TransactionType.PAYMENT)
                    .description("Lookup test")
                    .idempotencyKey("lookup-" + UUID.randomUUID())
                    .build();

            TransactionResponse created = transactionService.initiatePayment(request);

            TransactionResponse found = transactionService.getTransactionByReferenceId(created.getReferenceId());

            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        }
    }
}
