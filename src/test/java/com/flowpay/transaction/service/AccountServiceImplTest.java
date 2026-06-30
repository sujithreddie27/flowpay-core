package com.flowpay.transaction.service;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.common.exception.AccountNotActiveException;
import com.flowpay.common.exception.InsufficientFundsException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.monitoring.metrics.PaymentMetricsService;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.dto.UpdateAccountRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.mapper.AccountMapper;
import com.flowpay.transaction.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private PaymentMetricsService paymentMetricsService;

    @InjectMocks
    private AccountServiceImpl accountService;

    private UUID userId;
    private UUID accountId;
    private User user;
    private Account account;
    private AccountResponse accountResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        user = User.builder()
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("hashed")
                .build();
        user.setId(userId);

        account = Account.builder()
                .user(user)
                .accountNumber("1234567890")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        account.setId(accountId);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());

        accountResponse = AccountResponse.builder()
                .id(accountId)
                .userId(userId)
                .accountNumber("1234567890")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createAccount")
    class CreateAccountTests {

        @Test
        @DisplayName("should create account successfully")
        void shouldCreateAccountSuccessfully() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .userId(userId)
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .dailyLimit(new BigDecimal("5000"))
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(accountMapper.toEntity(request)).thenReturn(Account.builder().build());
            when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenReturn(account);
            when(accountMapper.toResponse(account)).thenReturn(accountResponse);

            AccountResponse result = accountService.createAccount(request);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(accountId);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void shouldThrowWhenUserNotFound() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .userId(userId)
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.createAccount(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAccountById")
    class GetAccountByIdTests {

        @Test
        @DisplayName("should return account when found")
        void shouldReturnAccountWhenFound() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountMapper.toResponse(account)).thenReturn(accountResponse);

            AccountResponse result = accountService.getAccountById(accountId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(accountId);
        }

        @Test
        @DisplayName("should throw exception when account not found")
        void shouldThrowWhenAccountNotFound() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccountById(accountId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAccountsByUserId")
    class GetAccountsByUserIdTests {

        @Test
        @DisplayName("should return accounts for user")
        void shouldReturnAccountsForUser() {
            when(userRepository.existsById(userId)).thenReturn(true);
            when(accountRepository.findByUserId(userId)).thenReturn(List.of(account));
            when(accountMapper.toResponse(account)).thenReturn(accountResponse);

            List<AccountResponse> result = accountService.getAccountsByUserId(userId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(accountId);
        }

        @Test
        @DisplayName("should return paged accounts for user")
        void shouldReturnPagedAccountsForUser() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Account> accountPage = new PageImpl<>(List.of(account), pageable, 1);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(accountRepository.findByUserId(userId, pageable)).thenReturn(accountPage);
            when(accountMapper.toResponse(account)).thenReturn(accountResponse);

            Page<AccountResponse> result = accountService.getAccountsByUserId(userId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(accountId);
        }

        @Test
        @DisplayName("should throw when user does not exist")
        void shouldThrowWhenUserDoesNotExist() {
            when(userRepository.existsById(userId)).thenReturn(false);

            assertThatThrownBy(() -> accountService.getAccountsByUserId(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("creditAccount")
    class CreditAccountTests {

        @Test
        @DisplayName("should credit account successfully")
        void shouldCreditAccountSuccessfully() {
            BigDecimal creditAmount = new BigDecimal("500.00");
            Account creditedAccount = Account.builder()
                    .user(user)
                    .accountNumber("1234567890")
                    .balance(new BigDecimal("1500.00"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .build();
            creditedAccount.setId(accountId);

            AccountResponse creditedResponse = AccountResponse.builder()
                    .id(accountId)
                    .balance(new BigDecimal("1500.00"))
                    .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(creditedAccount);
            when(accountMapper.toResponse(creditedAccount)).thenReturn(creditedResponse);

            AccountResponse result = accountService.creditAccount(accountId, creditAmount);

            assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw when amount is zero")
        void shouldThrowWhenAmountIsZero() {
            assertThatThrownBy(() -> accountService.creditAccount(accountId, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should throw when amount is negative")
        void shouldThrowWhenAmountIsNegative() {
            assertThatThrownBy(() -> accountService.creditAccount(accountId, new BigDecimal("-100")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should throw when account is not active")
        void shouldThrowWhenAccountIsNotActive() {
            account.setStatus(AccountStatus.FROZEN);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.creditAccount(accountId, new BigDecimal("100")))
                    .isInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw when account not found")
        void shouldThrowWhenAccountNotFound() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.creditAccount(accountId, new BigDecimal("100")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("debitAccount")
    class DebitAccountTests {

        @Test
        @DisplayName("should debit account successfully")
        void shouldDebitAccountSuccessfully() {
            BigDecimal debitAmount = new BigDecimal("200.00");
            Account debitedAccount = Account.builder()
                    .user(user)
                    .accountNumber("1234567890")
                    .balance(new BigDecimal("800.00"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .build();
            debitedAccount.setId(accountId);

            AccountResponse debitedResponse = AccountResponse.builder()
                    .id(accountId)
                    .balance(new BigDecimal("800.00"))
                    .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(debitedAccount);
            when(accountMapper.toResponse(debitedAccount)).thenReturn(debitedResponse);

            AccountResponse result = accountService.debitAccount(accountId, debitAmount);

            assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("should throw when insufficient funds")
        void shouldThrowWhenInsufficientFunds() {
            BigDecimal debitAmount = new BigDecimal("2000.00");
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debitAccount(accountId, debitAmount))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("should throw when amount is zero")
        void shouldThrowWhenAmountIsZero() {
            assertThatThrownBy(() -> accountService.debitAccount(accountId, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should throw when amount is negative")
        void shouldThrowWhenAmountIsNegative() {
            assertThatThrownBy(() -> accountService.debitAccount(accountId, new BigDecimal("-50")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("should throw when account is frozen")
        void shouldThrowWhenAccountIsFrozen() {
            account.setStatus(AccountStatus.FROZEN);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debitAccount(accountId, new BigDecimal("100")))
                    .isInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw when account is closed")
        void shouldThrowWhenAccountIsClosed() {
            account.setStatus(AccountStatus.CLOSED);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.debitAccount(accountId, new BigDecimal("100")))
                    .isInstanceOf(AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should allow debit of exact balance")
        void shouldAllowDebitOfExactBalance() {
            BigDecimal exactBalance = new BigDecimal("1000.00");
            Account debitedAccount = Account.builder()
                    .user(user)
                    .accountNumber("1234567890")
                    .balance(BigDecimal.ZERO)
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .build();
            debitedAccount.setId(accountId);

            AccountResponse debitedResponse = AccountResponse.builder()
                    .id(accountId)
                    .balance(BigDecimal.ZERO)
                    .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(debitedAccount);
            when(accountMapper.toResponse(debitedAccount)).thenReturn(debitedResponse);

            AccountResponse result = accountService.debitAccount(accountId, exactBalance);

            assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("updateAccount")
    class UpdateAccountTests {

        @Test
        @DisplayName("should update account status")
        void shouldUpdateAccountStatus() {
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .status(AccountStatus.FROZEN)
                    .build();

            Account updatedAccount = Account.builder()
                    .user(user)
                    .accountNumber("1234567890")
                    .balance(new BigDecimal("1000.00"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.FROZEN)
                    .build();
            updatedAccount.setId(accountId);

            AccountResponse updatedResponse = AccountResponse.builder()
                    .id(accountId)
                    .status(AccountStatus.FROZEN)
                    .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(updatedAccount);
            when(accountMapper.toResponse(updatedAccount)).thenReturn(updatedResponse);

            AccountResponse result = accountService.updateAccount(accountId, request);

            assertThat(result.getStatus()).isEqualTo(AccountStatus.FROZEN);
        }

        @Test
        @DisplayName("should update daily limit")
        void shouldUpdateDailyLimit() {
            BigDecimal newLimit = new BigDecimal("20000.00");
            UpdateAccountRequest request = UpdateAccountRequest.builder()
                    .dailyLimit(newLimit)
                    .build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any(Account.class))).thenReturn(account);
            when(accountMapper.toResponse(account)).thenReturn(accountResponse);

            accountService.updateAccount(accountId, request);

            verify(accountRepository).save(any(Account.class));
        }
    }

    @Nested
    @DisplayName("getBalance")
    class GetBalanceTests {

        @Test
        @DisplayName("should return balance")
        void shouldReturnBalance() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            BigDecimal balance = accountService.getBalance(accountId);

            assertThat(balance).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("should throw when account not found")
        void shouldThrowWhenAccountNotFound() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getBalance(accountId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTotalBalance")
    class GetTotalBalanceTests {

        @Test
        @DisplayName("should return total balance for user")
        void shouldReturnTotalBalanceForUser() {
            when(userRepository.existsById(userId)).thenReturn(true);
            when(accountRepository.getTotalBalanceByUserId(userId)).thenReturn(new BigDecimal("5000.00"));

            BigDecimal totalBalance = accountService.getTotalBalance(userId);

            assertThat(totalBalance).isEqualByComparingTo(new BigDecimal("5000.00"));
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.existsById(userId)).thenReturn(false);

            assertThatThrownBy(() -> accountService.getTotalBalance(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
