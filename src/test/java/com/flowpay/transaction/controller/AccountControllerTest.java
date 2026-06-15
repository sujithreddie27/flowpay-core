package com.flowpay.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.common.exception.InsufficientFundsException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.BalanceOperationRequest;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.dto.UpdateAccountRequest;
import com.flowpay.transaction.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    private final UUID accountId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private AccountResponse buildAccountResponse() {
        return AccountResponse.builder()
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

    @Test
    @DisplayName("POST /api/v1/accounts - should create account")
    void shouldCreateAccount() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .userId(userId)
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .build();

        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(buildAccountResponse());

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(accountId.toString()))
                .andExpect(jsonPath("$.data.accountNumber").value("1234567890"));
    }

    @Test
    @DisplayName("POST /api/v1/accounts - should return 400 for invalid request")
    void shouldReturn400ForInvalidCreateRequest() throws Exception {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .currency("INVALID")
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - should return account")
    void shouldReturnAccount() throws Exception {
        when(accountService.getAccountById(accountId)).thenReturn(buildAccountResponse());

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(accountId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - should return 404 when not found")
    void shouldReturn404WhenAccountNotFound() throws Exception {
        when(accountService.getAccountById(accountId))
                .thenThrow(new ResourceNotFoundException("Account", accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/user/{userId} - should return user accounts")
    void shouldReturnUserAccounts() throws Exception {
        when(accountService.getAccountsByUserId(userId)).thenReturn(List.of(buildAccountResponse()));

        mockMvc.perform(get("/api/v1/accounts/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(accountId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/credit - should credit account")
    void shouldCreditAccount() throws Exception {
        BalanceOperationRequest request = BalanceOperationRequest.builder()
                .amount(new BigDecimal("500.00"))
                .build();

        AccountResponse response = buildAccountResponse();
        response.setBalance(new BigDecimal("1500.00"));

        when(accountService.creditAccount(eq(accountId), any(BigDecimal.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/{id}/credit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(1500.00));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/debit - should debit account")
    void shouldDebitAccount() throws Exception {
        BalanceOperationRequest request = BalanceOperationRequest.builder()
                .amount(new BigDecimal("200.00"))
                .build();

        AccountResponse response = buildAccountResponse();
        response.setBalance(new BigDecimal("800.00"));

        when(accountService.debitAccount(eq(accountId), any(BigDecimal.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts/{id}/debit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balance").value(800.00));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/debit - should return 422 for insufficient funds")
    void shouldReturn422ForInsufficientFunds() throws Exception {
        BalanceOperationRequest request = BalanceOperationRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .build();

        when(accountService.debitAccount(eq(accountId), any(BigDecimal.class)))
                .thenThrow(new InsufficientFundsException(new BigDecimal("5000.00"), new BigDecimal("1000.00")));

        mockMvc.perform(post("/api/v1/accounts/{id}/debit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/accounts/{id} - should update account")
    void shouldUpdateAccount() throws Exception {
        UpdateAccountRequest request = UpdateAccountRequest.builder()
                .status(AccountStatus.FROZEN)
                .build();

        AccountResponse response = buildAccountResponse();
        response.setStatus(AccountStatus.FROZEN);

        when(accountService.updateAccount(eq(accountId), any(UpdateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/accounts/{id}", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FROZEN"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance - should return balance")
    void shouldReturnBalance() throws Exception {
        when(accountService.getBalance(accountId)).thenReturn(new BigDecimal("1000.00"));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1000.00));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/user/{userId}/total-balance - should return total balance")
    void shouldReturnTotalBalance() throws Exception {
        when(accountService.getTotalBalance(userId)).thenReturn(new BigDecimal("5000.00"));

        mockMvc.perform(get("/api/v1/accounts/user/{userId}/total-balance", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5000.00));
    }
}
