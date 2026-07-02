package com.flowpay.transaction.repository;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.transaction.entity.Account;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("account-test@test.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Account")
                .lastName("TestUser")
                .phone("+1111111111")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        user = entityManager.persistAndFlush(user);
    }

    private Account createAndPersistAccount(String accountNumber, BigDecimal balance,
                                             String currency, AccountType type, AccountStatus status) {
        Account account = Account.builder()
                .user(user)
                .accountNumber(accountNumber)
                .balance(balance)
                .currency(currency)
                .accountType(type)
                .status(status)
                .dailyLimit(new BigDecimal("50000.0000"))
                .build();
        return entityManager.persistAndFlush(account);
    }

    @Nested
    @DisplayName("FindByAccountNumber")
    class FindByAccountNumber {

        @Test
        @DisplayName("should find account by account number")
        void shouldFindByAccountNumber() {
            createAndPersistAccount("ACC-NUM-001", new BigDecimal("5000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);

            Optional<Account> result = accountRepository.findByAccountNumber("ACC-NUM-001");

            assertThat(result).isPresent();
            assertThat(result.get().getAccountNumber()).isEqualTo("ACC-NUM-001");
            assertThat(result.get().getBalance()).isEqualByComparingTo(new BigDecimal("5000.0000"));
        }

        @Test
        @DisplayName("should return empty for non-existent account number")
        void shouldReturnEmptyForNonExistent() {
            Optional<Account> result = accountRepository.findByAccountNumber("NON-EXISTENT");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("FindByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should find all accounts for a user")
        void shouldFindAllForUser() {
            createAndPersistAccount("ACC-U1", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-U2", new BigDecimal("2000.0000"), "EUR", AccountType.CURRENT, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-U3", new BigDecimal("500.0000"), "USD", AccountType.WALLET, AccountStatus.FROZEN);

            List<Account> accounts = accountRepository.findByUserId(user.getId());

            assertThat(accounts).hasSize(3);
        }

        @Test
        @DisplayName("should return empty for user with no accounts")
        void shouldReturnEmptyForUserWithNoAccounts() {
            User otherUser = User.builder()
                    .email("noaccounts@test.com")
                    .passwordHash("$2a$12$hashedpassword")
                    .firstName("No")
                    .lastName("Accounts")
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .role(UserRole.USER)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build();
            otherUser = entityManager.persistAndFlush(otherUser);

            List<Account> accounts = accountRepository.findByUserId(otherUser.getId());
            assertThat(accounts).isEmpty();
        }
    }

    @Nested
    @DisplayName("FindActiveAccountsByUserId")
    class FindActiveAccountsByUserId {

        @Test
        @DisplayName("should find only active accounts for user")
        void shouldFindOnlyActiveAccounts() {
            createAndPersistAccount("ACC-A1", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-A2", new BigDecimal("2000.0000"), "USD", AccountType.CURRENT, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-A3", new BigDecimal("500.0000"), "USD", AccountType.WALLET, AccountStatus.FROZEN);
            createAndPersistAccount("ACC-A4", new BigDecimal("0.0000"), "USD", AccountType.SAVINGS, AccountStatus.CLOSED);

            List<Account> activeAccounts = accountRepository.findActiveAccountsByUserId(user.getId());

            assertThat(activeAccounts).hasSize(2);
            assertThat(activeAccounts).allMatch(a -> a.getStatus() == AccountStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("FindByStatus")
    class FindByStatus {

        @Test
        @DisplayName("should find accounts by status")
        void shouldFindByStatus() {
            createAndPersistAccount("ACC-S1", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-S2", new BigDecimal("2000.0000"), "USD", AccountType.CURRENT, AccountStatus.FROZEN);

            List<Account> active = accountRepository.findByStatus(AccountStatus.ACTIVE);
            List<Account> frozen = accountRepository.findByStatus(AccountStatus.FROZEN);

            assertThat(active).hasSize(1);
            assertThat(frozen).hasSize(1);
        }
    }

    @Nested
    @DisplayName("FindByUserIdAndCurrency")
    class FindByUserIdAndCurrency {

        @Test
        @DisplayName("should find account by user and currency")
        void shouldFindByUserIdAndCurrency() {
            createAndPersistAccount("ACC-C1", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-C2", new BigDecimal("2000.0000"), "EUR", AccountType.SAVINGS, AccountStatus.ACTIVE);

            Optional<Account> usdAccount = accountRepository.findByUserIdAndCurrency(user.getId(), "USD");
            Optional<Account> eurAccount = accountRepository.findByUserIdAndCurrency(user.getId(), "EUR");
            Optional<Account> gbpAccount = accountRepository.findByUserIdAndCurrency(user.getId(), "GBP");

            assertThat(usdAccount).isPresent();
            assertThat(eurAccount).isPresent();
            assertThat(gbpAccount).isEmpty();
        }
    }

    @Nested
    @DisplayName("BalanceQueries")
    class BalanceQueries {

        @Test
        @DisplayName("should find accounts with balance greater than specified amount")
        void shouldFindWithBalanceGreaterThan() {
            createAndPersistAccount("ACC-B1", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-B2", new BigDecimal("5000.0000"), "USD", AccountType.CURRENT, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-B3", new BigDecimal("100.0000"), "USD", AccountType.WALLET, AccountStatus.ACTIVE);

            List<Account> highBalance = accountRepository.findAccountsWithBalanceGreaterThan(new BigDecimal("2000.0000"));

            assertThat(highBalance).hasSize(1);
            assertThat(highBalance.get(0).getAccountNumber()).isEqualTo("ACC-B2");
        }

        @Test
        @DisplayName("should find active accounts with balance less than specified amount")
        void shouldFindWithBalanceLessThan() {
            createAndPersistAccount("ACC-BL1", new BigDecimal("100.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-BL2", new BigDecimal("50.0000"), "USD", AccountType.CURRENT, AccountStatus.ACTIVE);
            createAndPersistAccount("ACC-BL3", new BigDecimal("50.0000"), "USD", AccountType.WALLET, AccountStatus.FROZEN);

            List<Account> lowBalance = accountRepository.findAccountsWithBalanceLessThan(new BigDecimal("200.0000"));

            assertThat(lowBalance).hasSize(2);
        }
    }

    @Nested
    @DisplayName("ExistsByAccountNumber")
    class ExistsByAccountNumber {

        @Test
        @DisplayName("should return true for existing account number")
        void shouldReturnTrueForExisting() {
            createAndPersistAccount("ACC-EX-001", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);

            assertThat(accountRepository.existsByAccountNumber("ACC-EX-001")).isTrue();
            assertThat(accountRepository.existsByAccountNumber("ACC-NONEXIST")).isFalse();
        }
    }

    @Nested
    @DisplayName("OptimisticLocking")
    class OptimisticLocking {

        @Test
        @DisplayName("should have version field for optimistic locking")
        void shouldHaveVersionField() {
            Account account = createAndPersistAccount("ACC-VER-001", new BigDecimal("1000.0000"), "USD", AccountType.SAVINGS, AccountStatus.ACTIVE);

            assertThat(account.getVersion()).isNotNull();

            account.setBalance(new BigDecimal("2000.0000"));
            entityManager.persistAndFlush(account);

            Account updated = entityManager.find(Account.class, account.getId());
            assertThat(updated.getVersion()).isGreaterThan(0L);
        }
    }
}
