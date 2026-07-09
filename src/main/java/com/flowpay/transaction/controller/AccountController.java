package com.flowpay.transaction.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.BalanceOperationRequest;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.dto.UpdateAccountRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(
            @Parameter(description = "Account UUID") @PathVariable UUID accountId) {
        BigDecimal balance = accountService.getBalance(accountId);
        return ResponseEntity.ok(ApiResponse.success(balance));
    }

    @GetMapping("/user/{userId}/total-balance")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#userId)")
    @Operation(summary = "Get total balance", description = "Get the total balance across all accounts for a user")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalBalance(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        BigDecimal totalBalance = accountService.getTotalBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(totalBalance));
    }
}
