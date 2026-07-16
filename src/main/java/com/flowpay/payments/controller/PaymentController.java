package com.flowpay.payments.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.common.ratelimit.RateLimited;
import com.flowpay.payments.dto.PaymentFilterRequest;
import com.flowpay.payments.service.PaymentService;
import com.flowpay.security.CustomUserDetails;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
@Tag(name = "Payments", description = "Payment management APIs")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "List payments", description = "List payments for the current user")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> listPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute PaymentFilterRequest filter) {
        PagedResponse<TransactionResponse> response = paymentService.listPayments(userDetails.getUserId(), filter);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment detail")
    public ResponseEntity<ApiResponse<TransactionResponse>> getPayment(@PathVariable UUID id) {
        TransactionResponse response = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @RateLimited(limit = 50, windowSeconds = 60)
    @Operation(summary = "Initiate payment", description = "Create a new payment")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiatePayment(
            @Valid @RequestBody InitiateTransactionRequest request) {
        TransactionResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment initiated successfully"));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm payment", description = "Confirm a pending payment")
    public ResponseEntity<ApiResponse<TransactionResponse>> confirmPayment(@PathVariable UUID id) {
        TransactionResponse response = paymentService.confirmPayment(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment confirmed successfully"));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry payment", description = "Retry a failed payment")
    public ResponseEntity<ApiResponse<TransactionResponse>> retryPayment(@PathVariable UUID id) {
        TransactionResponse response = paymentService.retryPayment(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment retry initiated"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel payment", description = "Cancel a pending payment")
    public ResponseEntity<ApiResponse<TransactionResponse>> cancelPayment(@PathVariable UUID id) {
        TransactionResponse response = paymentService.cancelPayment(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment cancelled successfully"));
    }
}
