package com.flowpay.transaction.service;

import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.dto.UpdateAccountRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccountById(UUID accountId);

    AccountResponse getAccountByAccountNumber(String accountNumber);

    List<AccountResponse> getAccountsByUserId(UUID userId);

    Page<AccountResponse> getAccountsByUserId(UUID userId, Pageable pageable);

    AccountResponse updateAccount(UUID accountId, UpdateAccountRequest request);

    AccountResponse creditAccount(UUID accountId, BigDecimal amount);

    AccountResponse debitAccount(UUID accountId, BigDecimal amount);

    BigDecimal getBalance(UUID accountId);

    BigDecimal getTotalBalance(UUID userId);
}
