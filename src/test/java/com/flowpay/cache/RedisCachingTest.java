package com.flowpay.cache;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.config.RedisConfig;
import com.flowpay.monitoring.metrics.PaymentMetricsService;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.UpdateAccountRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.mapper.AccountMapper;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.service.AccountService;
import com.flowpay.transaction.service.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(RedisCachingTest.CacheTestConfig.class)
@DisplayName("Redis Caching Behavior Tests")
class RedisCachingTest {

    @EnableCaching
    @Import(AccountServiceImpl.class)
    static class CacheTestConfig {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    RedisConfig.CACHE_ACCOUNT,
                    RedisConfig.CACHE_ACCOUNT_BALANCE,
                    RedisConfig.CACHE_USER_PROFILE,
                    RedisConfig.CACHE_TRANSACTION_STATUS
            );
        }

        @Bean
        public AccountRepository accountRepository() {
            return mock(AccountRepository.class);
        }

        @Bean
        public UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        public AccountMapper accountMapper() {
            return mock(AccountMapper.class);
        }

        @Bean
        public PaymentMetricsService paymentMetricsService() {
            return mock(PaymentMetricsService.class);
        }
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountMapper accountMapper;

    private UUID accountId;
    private Account account;
    private AccountResponse accountResponse;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("User")
                .build();

        account = Account.builder()
                .accountNumber("1234567890")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("5000.00"))
                .user(user)
                .build();

        accountResponse = AccountResponse.builder()
                .id(accountId)
                .accountNumber("1234567890")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .build();

        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });

        // Reset mocks
        reset(accountRepository, accountMapper);
    }

    @Nested
    @DisplayName("Account Balance Caching")
    class AccountBalanceCaching {

        @Test
        @DisplayName("Should cache account balance on first call")
        void shouldCacheBalanceOnFirstCall() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            BigDecimal balance1 = accountService.getBalance(accountId);
            BigDecimal balance2 = accountService.getBalance(accountId);

            assertThat(balance1).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(balance2).isEqualByComparingTo(new BigDecimal("1000.00"));
            verify(accountRepository, times(1)).findById(accountId);
        }

        @Test
        @DisplayName("Should evict balance cache on credit")
        void shouldEvictCacheOnCredit() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(accountMapper.toResponse(any())).thenReturn(accountResponse);

            // Populate cache
            accountService.getBalance(accountId);

            // Credit should evict cache
            accountService.creditAccount(accountId, new BigDecimal("100.00"));

            // Should hit DB again
            accountService.getBalance(accountId);

            verify(accountRepository, times(3)).findById(accountId);
        }

        @Test
        @DisplayName("Should evict balance cache on debit")
        void shouldEvictCacheOnDebit() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(accountMapper.toResponse(any())).thenReturn(accountResponse);

            // Populate cache
            accountService.getBalance(accountId);

            // Debit should evict cache
            accountService.debitAccount(accountId, new BigDecimal("50.00"));

            // Should hit DB again
            accountService.getBalance(accountId);

            verify(accountRepository, times(3)).findById(accountId);
        }
    }

    @Nested
    @DisplayName("Account Details Caching")
    class AccountDetailsCaching {

        @Test
        @DisplayName("Should cache account details on first call")
        void shouldCacheAccountDetailsOnFirstCall() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountMapper.toResponse(account)).thenReturn(accountResponse);

            AccountResponse result1 = accountService.getAccountById(accountId);
            AccountResponse result2 = accountService.getAccountById(accountId);

            assertThat(result1.getAccountNumber()).isEqualTo("1234567890");
            assertThat(result2.getAccountNumber()).isEqualTo("1234567890");
            verify(accountRepository, times(1)).findById(accountId);
        }

        @Test
        @DisplayName("Should evict account cache on update")
        void shouldEvictCacheOnUpdate() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(accountMapper.toResponse(any())).thenReturn(accountResponse);

            // Populate cache
            accountService.getAccountById(accountId);

            // Update should evict cache
            UpdateAccountRequest updateRequest = UpdateAccountRequest.builder()
                    .dailyLimit(new BigDecimal("10000.00"))
                    .build();
            accountService.updateAccount(accountId, updateRequest);

            // Should hit DB again
            accountService.getAccountById(accountId);

            verify(accountRepository, times(3)).findById(accountId);
        }
    }
}
