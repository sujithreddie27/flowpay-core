package com.flowpay.transaction.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.security.CustomUserDetails;
import com.flowpay.transaction.dto.*;
import com.flowpay.transaction.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management and balance operations")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Create an account", description = "Create a new financial account for a user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created successfully"));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get account by ID", description = "Retrieve an account by its unique identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        AccountResponse response = accountService.getAccountById(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/number/{accountNumber}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get account by number", description = "Retrieve an account by its account number")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountByNumber(
            @Parameter(description = "Account number string") @PathVariable String accountNumber) {
        AccountResponse response = accountService.getAccountByAccountNumber(accountNumber);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#userId)")
    @Operation(summary = "Get accounts by user", description = "Retrieve all accounts for a specific user")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsByUserId(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        List<AccountResponse> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/user/{userId}/paged")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#userId)")
    @Operation(summary = "Get accounts by user (paged)", description = "Retrieve paginated accounts for a specific user")
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> getAccountsByUserIdPaged(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AccountResponse> accountPage = accountService.getAccountsByUserId(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(accountPage)));
    }

    @PatchMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update account", description = "Update account details (Admin only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        AccountResponse response = accountService.updateAccount(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Account updated successfully"));
    }

    @PostMapping("/{accountId}/credit")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Credit account", description = "Add funds to an account")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account credited"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<ApiResponse<AccountResponse>> creditAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId,
            @Valid @RequestBody BalanceOperationRequest request) {
        AccountResponse response = accountService.creditAccount(accountId, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success(response, "Account credited successfully"));
    }

    @PostMapping("/{accountId}/debit")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Debit account", description = "Withdraw funds from an account")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account debited"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Insufficient funds")
    })
    public ResponseEntity<ApiResponse<AccountResponse>> debitAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId,
            @Valid @RequestBody BalanceOperationRequest request) {
        AccountResponse response = accountService.debitAccount(accountId, request.getAmount());
        return ResponseEntity.ok(ApiResponse.success(response, "Account debited successfully"));
    }

    @GetMapping("/{accountId}/balance")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get account balance", description = "Retrieve the current balance of an account")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        AccountResponse account = accountService.getAccountById(accountId);
        BalanceResponse response = BalanceResponse.builder()
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/{userId}/total-balance")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#userId)")
    @Operation(summary = "Get total balance", description = "Get the total balance across all accounts for a user")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalBalance(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        BigDecimal totalBalance = accountService.getTotalBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(totalBalance));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get current user's accounts", description = "List accounts for the authenticated user")
    public ResponseEntity<ApiResponse<PagedResponse<AccountResponse>>> getCurrentUserAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AccountResponse> accountPage = accountService.getAccountsByUserId(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(accountPage)));
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Close account", description = "Soft-close an account by setting status to CLOSED")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        AccountResponse response = accountService.closeAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(response, "Account closed successfully"));
    }

    @GetMapping("/{accountId}/balance/history")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get balance history", description = "Query transactions for balance timeline")
    public ResponseEntity<ApiResponse<List<BalanceHistoryEntry>>> getBalanceHistory(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        List<BalanceHistoryEntry> history = accountService.getBalanceHistory(accountId, from, to);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @PostMapping("/{accountId}/freeze")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Freeze account", description = "Set account status to FROZEN")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        AccountResponse response = accountService.freezeAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(response, "Account frozen successfully"));
    }

    @PostMapping("/{accountId}/unfreeze")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Unfreeze account", description = "Set account status back to ACTIVE")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        AccountResponse response = accountService.unfreezeAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(response, "Account unfrozen successfully"));
    }

    @GetMapping("/{accountId}/statement")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get account statement", description = "Build statement from transactions")
    public ResponseEntity<ApiResponse<AccountStatementResponse>> getStatement(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        AccountStatementResponse response = accountService.getStatement(accountId, from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
