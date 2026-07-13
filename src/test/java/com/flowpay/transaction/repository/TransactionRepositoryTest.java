package com.flowpay.transaction.repository;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("docker")
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("flowpay_repo_test")
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
    private EntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private User sender;
    private User receiver;
    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        sender = userRepository.saveAndFlush(User.builder()
                .email("sender@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Sender")
                .lastName("User")
                .phone("+1234567890")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        receiver = userRepository.saveAndFlush(User.builder()
                .email("receiver@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Receiver")
                .lastName("User")
                .phone("+0987654321")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        senderAccount = accountRepository.saveAndFlush(Account.builder()
                .user(sender)
                .accountNumber("ACC-SENDER-001")
                .balance(new BigDecimal("10000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());

        receiverAccount = accountRepository.saveAndFlush(Account.builder()
                .user(receiver)
                .accountNumber("ACC-RECV-001")
                .balance(new BigDecimal("5000.0000"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build());
    }

    private Transaction createAndSaveTransaction(String referenceId, String idempotencyKey,
                                                  TransactionStatus status, TransactionType type,
                                                  BigDecimal amount) {
        Transaction tx = Transaction.builder()
                .referenceId(referenceId)
                .idempotencyKey(idempotencyKey)
                .sender(sender)
                .receiver(receiver)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(amount)
                .currency("USD")
                .fee(BigDecimal.ZERO)
                .status(status)
                .type(type)
                .description("Test transaction")
                .build();
        return transactionRepository.saveAndFlush(tx);
    }

    @Nested
    @DisplayName("FindByReferenceId")
    class FindByReferenceId {

        @Test
        @DisplayName("should find transaction by reference ID")
        void shouldFindByReferenceId() {
            createAndSaveTransaction("TXN-REF-001", "IDEM-001",
                    TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));

            Optional<Transaction> result = transactionRepository.findByReferenceId("TXN-REF-001");

            assertThat(result).isPresent();
            assertThat(result.get().getReferenceId()).isEqualTo("TXN-REF-001");
        }

        @Test
        @DisplayName("should return empty for non-existent reference ID")
        void shouldReturnEmptyForNonExistentReferenceId() {
            Optional<Transaction> result = transactionRepository.findByReferenceId("NON-EXISTENT");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("FindByIdempotencyKey")
    class FindByIdempotencyKey {

        @Test
        @DisplayName("should find transaction by idempotency key")
        void shouldFindByIdempotencyKey() {
            createAndSaveTransaction("TXN-REF-002", "IDEM-002",
                    TransactionStatus.PENDING, TransactionType.PAYMENT, new BigDecimal("250.00"));

            Optional<Transaction> result = transactionRepository.findByIdempotencyKey("IDEM-002");

            assertThat(result).isPresent();
            assertThat(result.get().getIdempotencyKey()).isEqualTo("IDEM-002");
            assertThat(result.get().getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        }

        @Test
        @DisplayName("should return empty for null idempotency key")
        void shouldReturnEmptyForNullIdempotencyKey() {
            Optional<Transaction> result = transactionRepository.findByIdempotencyKey(null);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("FindByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should find all transactions with given status")
        void shouldFindByStatus() {
            createAndSaveTransaction("TXN-01", "IDM-01", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));
            createAndSaveTransaction("TXN-02", "IDM-02", TransactionStatus.COMPLETED, TransactionType.PAYMENT, new BigDecimal("200.00"));
            createAndSaveTransaction("TXN-03", "IDM-03", TransactionStatus.FAILED, TransactionType.TRANSFER, new BigDecimal("300.00"));

            List<Transaction> completed = transactionRepository.findByStatus(TransactionStatus.COMPLETED);
            List<Transaction> failed = transactionRepository.findByStatus(TransactionStatus.FAILED);

            assertThat(completed).hasSize(2);
            assertThat(failed).hasSize(1);
        }

        @Test
        @DisplayName("should support pagination when finding by status")
        void shouldSupportPaginationByStatus() {
            for (int i = 0; i < 15; i++) {
                createAndSaveTransaction("TXN-P" + i, "IDM-P" + i,
                        TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("50.00"));
            }

            Page<Transaction> page = transactionRepository.findByStatus(
                    TransactionStatus.COMPLETED, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(10);
            assertThat(page.getTotalElements()).isEqualTo(15);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("FindByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should find transactions where user is sender or receiver")
        void shouldFindByUserId() {
            createAndSaveTransaction("TXN-U1", "IDM-U1", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));
            createAndSaveTransaction("TXN-U2", "IDM-U2", TransactionStatus.PENDING, TransactionType.PAYMENT, new BigDecimal("200.00"));

            Page<Transaction> senderTxns = transactionRepository.findByUserId(sender.getId(), Pageable.unpaged());
            Page<Transaction> receiverTxns = transactionRepository.findByUserId(receiver.getId(), Pageable.unpaged());

            assertThat(senderTxns.getContent()).hasSize(2);
            assertThat(receiverTxns.getContent()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("FindStalePendingTransactions")
    class FindStalePendingTransactions {

        @Test
        @DisplayName("should find pending transactions older than threshold")
        void shouldFindStalePendingTransactions() {
            Transaction staleTx = transactionRepository.saveAndFlush(Transaction.builder()
                    .referenceId("TXN-STALE")
                    .idempotencyKey("IDM-STALE")
                    .sender(sender)
                    .receiver(receiver)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.PENDING)
                    .type(TransactionType.TRANSFER)
                    .build());

            // Manually update created_at to be older than threshold
            entityManager.createQuery("UPDATE Transaction t SET t.createdAt = :date WHERE t.id = :id")
                    .setParameter("date", OffsetDateTime.now().minusHours(2))
                    .setParameter("id", staleTx.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();

            List<Transaction> stale = transactionRepository.findStalePendingTransactions(
                    OffsetDateTime.now().minusMinutes(30));

            assertThat(stale).hasSize(1);
            assertThat(stale.get(0).getReferenceId()).isEqualTo("TXN-STALE");
        }
    }

    @Nested
    @DisplayName("FindRetryableTransactions")
    class FindRetryableTransactions {

        @Test
        @DisplayName("should find failed transactions with retry count less than 3")
        void shouldFindRetryableTransactions() {
            transactionRepository.saveAndFlush(Transaction.builder()
                    .referenceId("TXN-RETRY")
                    .idempotencyKey("IDM-RETRY")
                    .sender(sender)
                    .receiver(receiver)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.FAILED)
                    .type(TransactionType.TRANSFER)
                    .retryCount(1)
                    .build());

            transactionRepository.saveAndFlush(Transaction.builder()
                    .referenceId("TXN-EXHAUSTED")
                    .idempotencyKey("IDM-EXHAUSTED")
                    .sender(sender)
                    .receiver(receiver)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("200.00"))
                    .currency("USD")
                    .fee(BigDecimal.ZERO)
                    .status(TransactionStatus.FAILED)
                    .type(TransactionType.TRANSFER)
                    .retryCount(3)
                    .build());

            List<Transaction> retryables = transactionRepository.findRetryableTransactions();

            assertThat(retryables).hasSize(1);
            assertThat(retryables.get(0).getReferenceId()).isEqualTo("TXN-RETRY");
        }
    }

    @Nested
    @DisplayName("AggregateQueries")
    class AggregateQueries {

        @Test
        @DisplayName("should calculate total amount by sender in date range")
        void shouldCalculateTotalAmountBySender() {
            createAndSaveTransaction("TXN-A1", "IDM-A1", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));
            createAndSaveTransaction("TXN-A2", "IDM-A2", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("200.00"));
            createAndSaveTransaction("TXN-A3", "IDM-A3", TransactionStatus.FAILED, TransactionType.TRANSFER, new BigDecimal("500.00"));

            OffsetDateTime startOfDay = OffsetDateTime.now().minusDays(1);
            OffsetDateTime endOfDay = OffsetDateTime.now().plusDays(1);

            BigDecimal total = transactionRepository.getTotalAmountBySenderIdAndDateRange(
                    sender.getId(), startOfDay, endOfDay);

            assertThat(total).isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("should return zero when no completed transactions in range")
        void shouldReturnZeroWhenNoCompletedTransactions() {
            BigDecimal total = transactionRepository.getTotalAmountBySenderIdAndDateRange(
                    sender.getId(),
                    OffsetDateTime.now().minusDays(1),
                    OffsetDateTime.now().plusDays(1));

            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should count transactions by status")
        void shouldCountByStatus() {
            createAndSaveTransaction("TXN-C1", "IDM-C1", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));
            createAndSaveTransaction("TXN-C2", "IDM-C2", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("200.00"));
            createAndSaveTransaction("TXN-C3", "IDM-C3", TransactionStatus.PENDING, TransactionType.TRANSFER, new BigDecimal("300.00"));

            long completedCount = transactionRepository.countByStatus(TransactionStatus.COMPLETED);
            long pendingCount = transactionRepository.countByStatus(TransactionStatus.PENDING);

            assertThat(completedCount).isEqualTo(2);
            assertThat(pendingCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DateRangeQueries")
    class DateRangeQueries {

        @Test
        @DisplayName("should find transactions within date range")
        void shouldFindByDateRange() {
            createAndSaveTransaction("TXN-D1", "IDM-D1", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));
            createAndSaveTransaction("TXN-D2", "IDM-D2", TransactionStatus.COMPLETED, TransactionType.PAYMENT, new BigDecimal("200.00"));

            List<Transaction> result = transactionRepository.findByDateRange(
                    OffsetDateTime.now().minusHours(1),
                    OffsetDateTime.now().plusHours(1));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for future date range")
        void shouldReturnEmptyForFutureDateRange() {
            createAndSaveTransaction("TXN-D3", "IDM-D3", TransactionStatus.COMPLETED, TransactionType.TRANSFER, new BigDecimal("100.00"));

            List<Transaction> result = transactionRepository.findByDateRange(
                    OffsetDateTime.now().plusDays(1),
                    OffsetDateTime.now().plusDays(2));

            assertThat(result).isEmpty();
        }
    }
}
