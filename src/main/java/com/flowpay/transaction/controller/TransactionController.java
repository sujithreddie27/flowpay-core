package com.flowpay.transaction.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.common.ratelimit.RateLimited;
import com.flowpay.transaction.dto.*;
import com.flowpay.transaction.service.BatchTransactionService;
import com.flowpay.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Payment transaction management APIs")
public class TransactionController {

    private final TransactionService transactionService;
    private final BatchTransactionService batchTransactionService;

    @PostMapping
    @RateLimited(limit = 50, windowSeconds = 60)
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Initiate a payment", description = "Create a new payment transaction between accounts")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Payment initiated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate transaction"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Insufficient funds or validation failure")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> initiatePayment(
            @Valid @RequestBody InitiateTransactionRequest request) {
        TransactionResponse response = transactionService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment initiated successfully"));
    }

    @GetMapping("/{transactionId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get transaction by ID", description = "Retrieve a transaction by its unique identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @Parameter(description = "Transaction UUID") @PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reference/{referenceId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get transaction by reference ID", description = "Retrieve a transaction by its reference identifier")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionByReferenceId(
            @Parameter(description = "Transaction reference ID") @PathVariable String referenceId) {
        TransactionResponse response = transactionService.getTransactionByReferenceId(referenceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#userId)")
    @Operation(summary = "Get transactions by user", description = "Retrieve paginated transactions for a specific user (as sender or receiver)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transactions retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionsByUserId(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionsByUserId(userId, filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/sender/{senderId}")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#senderId)")
    @Operation(summary = "Get transactions by sender", description = "Retrieve paginated transactions sent by a specific user")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionsBySenderId(
            @Parameter(description = "Sender user UUID") @PathVariable UUID senderId,
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionsBySenderId(senderId, filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/receiver/{receiverId}")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#receiverId)")
    @Operation(summary = "Get transactions by receiver", description = "Retrieve paginated transactions received by a specific user")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionsByReceiverId(
            @Parameter(description = "Receiver user UUID") @PathVariable UUID receiverId,
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionsByReceiverId(receiverId, filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @PostMapping("/{transactionId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Cancel a transaction", description = "Cancel a pending transaction")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Transaction cannot be cancelled")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> cancelTransaction(
            @Parameter(description = "Transaction UUID") @PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.cancelTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction cancelled successfully"));
    }

    @PostMapping("/{transactionId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retry a failed transaction", description = "Retry a previously failed transaction (Admin only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Retry initiated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Transaction is not retryable")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> retryTransaction(
            @Parameter(description = "Transaction UUID") @PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.retryTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction retry initiated successfully"));
    }

    @PostMapping("/{transactionId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reverse a transaction", description = "Reverse a completed transaction (Admin only)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction reversed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> reverseTransaction(
            @Parameter(description = "Transaction UUID") @PathVariable UUID transactionId,
            @Parameter(description = "Reason for reversal") @RequestParam(required = false, defaultValue = "Manual reversal") String reason) {
        TransactionResponse response = transactionService.reverseTransaction(transactionId, reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction reversed successfully"));
    }

    @GetMapping("/retryable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get retryable transactions", description = "Retrieve all transactions eligible for retry (Admin only)")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getRetryableTransactions() {
        List<TransactionResponse> retryable = transactionService.getRetryableTransactions();
        return ResponseEntity.ok(ApiResponse.success(retryable));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get transaction history", description = "Retrieve paginated transaction history with filtering (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionHistory(
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionHistory(filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/summary/{userId}")
    @PreAuthorize("@resourceOwnershipValidator.isOwnerOrAdmin(#userId)")
    @Operation(summary = "Get transaction summary", description = "Get transaction statistics for a user within a date range")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getTransactionSummary(
            @Parameter(description = "User UUID") @PathVariable UUID userId,
            @Parameter(description = "Start date-time (ISO 8601)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "End date-time (ISO 8601)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        TransactionSummaryResponse summary = transactionService.getTransactionSummary(userId, from, to);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{transactionId}/receipt")
    @Operation(summary = "Get transaction receipt", description = "Retrieve a printable receipt for a completed transaction")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Receipt generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<ApiResponse<TransactionReceiptResponse>> getTransactionReceipt(
            @Parameter(description = "Transaction UUID") @PathVariable UUID transactionId) {
        TransactionReceiptResponse receipt = transactionService.getTransactionReceipt(transactionId);
        return ResponseEntity.ok(ApiResponse.success(receipt));
    }

    @PostMapping("/stale/process")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process stale transactions", description = "Process all stale pending transactions (Admin only)")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> processStalePendingTransactions() {
        int processed = transactionService.processStalePendingTransactions();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("processedCount", processed),
                "Stale pending transactions processed"
        ));
    }

    @PostMapping("/batch")
    @RateLimited(limit = 10, windowSeconds = 60)
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Process batch transactions", description = "Process multiple transactions in a single batch request")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Batch processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid batch request")
    })
    public ResponseEntity<ApiResponse<BatchTransactionResponse>> processBatch(
            @Valid @RequestBody BatchTransactionRequest request) {
        BatchTransactionResponse response = batchTransactionService.processBatch(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Batch processed successfully"));
    }

    @PostMapping("/batch/async")
    @RateLimited(limit = 5, windowSeconds = 60)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Process batch asynchronously", description = "Submit a batch of transactions for async processing (Admin only)")
    public ResponseEntity<ApiResponse<String>> processBatchAsync(
            @Valid @RequestBody BatchTransactionRequest request) {
        batchTransactionService.processBatchAsync(request);
        return ResponseEntity.accepted()
                .body(ApiResponse.success("Batch submitted for async processing"));
    }
}
