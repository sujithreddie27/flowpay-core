package com.flowpay.transaction.service;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.exception.AccountNotActiveException;
import com.flowpay.common.exception.InsufficientFundsException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.dto.UpdateAccountRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.mapper.AccountMapper;
import com.flowpay.transaction.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        Account account = accountMapper.toEntity(request);
        account.setUser(user);
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        if (request.getDailyLimit() != null) {
            account.setDailyLimit(request.getDailyLimit());
        }

        Account saved = accountRepository.save(account);
        log.info("Account created: id={}, userId={}, type={}", saved.getId(), user.getId(), saved.getAccountType());
        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        Account account = findAccountOrThrow(accountId);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId) {
        verifyUserExists(userId);
        return accountRepository.findByUserId(userId).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccountsByUserId(UUID userId, Pageable pageable) {
        verifyUserExists(userId);
        return accountRepository.findByUserId(userId, pageable)
                .map(accountMapper::toResponse);
    }

    @Override
    @Transactional
    public AccountResponse updateAccount(UUID accountId, UpdateAccountRequest request) {
        Account account = findAccountOrThrow(accountId);

        if (request.getStatus() != null) {
            account.setStatus(request.getStatus());
        }
        if (request.getDailyLimit() != null) {
            account.setDailyLimit(request.getDailyLimit());
        }

        Account updated = accountRepository.save(account);
        log.info("Account updated: id={}, status={}", updated.getId(), updated.getStatus());
        return accountMapper.toResponse(updated);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AccountResponse creditAccount(UUID accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        Account account = findAccountOrThrow(accountId);
        validateAccountActive(account);

        account.credit(amount);
        Account saved = accountRepository.save(account);

        log.info("Account credited: id={}, amount={}, newBalance={}", accountId, amount, saved.getBalance());
        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public AccountResponse debitAccount(UUID accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        Account account = findAccountOrThrow(accountId);
        validateAccountActive(account);

        if (!account.hasSufficientBalance(amount)) {
            throw new InsufficientFundsException(amount, account.getBalance());
        }

        account.debit(amount);
        Account saved = accountRepository.save(account);

        log.info("Account debited: id={}, amount={}, newBalance={}", accountId, amount, saved.getBalance());
        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId) {
        Account account = findAccountOrThrow(accountId);
        return account.getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(UUID userId) {
        verifyUserExists(userId);
        return accountRepository.getTotalBalanceByUserId(userId);
    }

    private Account findAccountOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private void validateAccountActive(Account account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.getId());
        }
    }

    private void verifyUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = String.format("%010d", ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
