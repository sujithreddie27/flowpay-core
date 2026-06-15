package com.flowpay.transaction.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.BalanceOperationRequest;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.dto.UpdateAccountRequest;
import com.flowpay.transaction.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created successfully"));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable UUID accountId) {
        AccountResponse response = accountService.getAccountById(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByNumber(
            @PathVariable String accountNumber) {
        AccountResponse response = accountService.getAccountByAccountNumber(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsByUserId(
            @PathVariable UUID userId) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/user/{userId}/paged")
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> getAccountsByUserIdPaged(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AccountResponse> accountPage = accountService.getAccountsByUserId(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(accountPage)));
    }

    @PatchMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        AccountResponse response = accountService.updateAccount(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Account updated successfully"));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<ApiResponse<AccountResponse>> creditAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody BalanceOperationRequest request) {
        AccountResponse response = accountService.creditAccount(accountId, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success(response, "Account credited successfully"));
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<ApiResponse<AccountResponse>> debitAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody BalanceOperationRequest request) {
        AccountResponse response = accountService.debitAccount(accountId, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success(response, "Account debited successfully"));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @PathVariable UUID accountId) {
        BigDecimal balance = accountService.getBalance(accountId);
        return ResponseEntity.ok(ApiResponse.success(balance));
    }

    @GetMapping("/user/{userId}/total-balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalBalance(
            @PathVariable UUID userId) {
        BigDecimal totalBalance = accountService.getTotalBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(totalBalance));
    }
}
