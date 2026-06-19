package com.flowpay.transaction.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.transaction.dto.*;
import com.flowpay.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> initiatePayment(
            @Valid @RequestBody InitiateTransactionRequest request) {
        TransactionResponse response = transactionService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment initiated successfully"));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reference/{referenceId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionByReferenceId(
            @PathVariable String referenceId) {
        TransactionResponse response = transactionService.getTransactionByReferenceId(referenceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionsByUserId(
            @PathVariable UUID userId,
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionsByUserId(userId, filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/sender/{senderId}")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionsBySenderId(
            @PathVariable UUID senderId,
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionsBySenderId(senderId, filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionsByReceiverId(
            @PathVariable UUID receiverId,
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionsByReceiverId(receiverId, filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @PostMapping("/{transactionId}/cancel")
    public ResponseEntity<ApiResponse<TransactionResponse>> cancelTransaction(
            @PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.cancelTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction cancelled successfully"));
    }

    @PostMapping("/{transactionId}/retry")
    public ResponseEntity<ApiResponse<TransactionResponse>> retryTransaction(
            @PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.retryTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction retry initiated successfully"));
    }

    @PostMapping("/{transactionId}/reverse")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverseTransaction(
            @PathVariable UUID transactionId,
            @RequestParam(required = false, defaultValue = "Manual reversal") String reason) {
        TransactionResponse response = transactionService.reverseTransaction(transactionId, reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction reversed successfully"));
    }

    @GetMapping("/retryable")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getRetryableTransactions() {
        List<TransactionResponse> retryable = transactionService.getRetryableTransactions();
        return ResponseEntity.ok(ApiResponse.success(retryable));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> getTransactionHistory(
            @ModelAttribute TransactionFilterRequest filter) {
        Page<TransactionResponse> page = transactionService.getTransactionHistory(filter);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
    }

    @GetMapping("/summary/{userId}")
    public ResponseEntity<ApiResponse<TransactionSummaryResponse>> getTransactionSummary(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        TransactionSummaryResponse summary = transactionService.getTransactionSummary(userId, from, to);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{transactionId}/receipt")
    public ResponseEntity<ApiResponse<TransactionReceiptResponse>> getTransactionReceipt(
            @PathVariable UUID transactionId) {
        TransactionReceiptResponse receipt = transactionService.getTransactionReceipt(transactionId);
        return ResponseEntity.ok(ApiResponse.success(receipt));
    }

    @PostMapping("/stale/process")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> processStalePendingTransactions() {
        int processed = transactionService.processStalePendingTransactions();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("processedCount", processed),
                "Stale pending transactions processed"
        ));
    }
}
