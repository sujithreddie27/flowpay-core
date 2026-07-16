package com.flowpay.admin.controller;

import com.flowpay.admin.dto.*;
import com.flowpay.admin.service.AdminService;
import com.flowpay.auth.dto.UserResponse;
import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.AuditLogFilterRequest;
import com.flowpay.common.dto.AuditLogResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administrative management APIs")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get system-wide metrics")
    public ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> getDashboardStats() {
        AdminDashboardStatsResponse response = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/processing-rate")
    @Operation(summary = "Get transaction processing rate (TPS)")
    public ResponseEntity<ApiResponse<ProcessingRateResponse>> getProcessingRate() {
        ProcessingRateResponse response = adminService.getProcessingRate();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/latency")
    @Operation(summary = "Get p50/p95/p99 latency")
    public ResponseEntity<ApiResponse<LatencyResponse>> getLatency() {
        LatencyResponse response = adminService.getLatency();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<UserResponse> response = adminService.listUsers(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get full user detail with accounts")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable UUID id) {
        AdminUserDetailResponse response = adminService.getUserDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/users/{id}")
    @Operation(summary = "Suspend or activate user")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        UserResponse response = adminService.updateUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "User status updated"));
    }

    @GetMapping("/transactions")
    @Operation(summary = "List all transactions (admin filter)")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionResponse>>> listTransactions(
            @ModelAttribute TransactionFilterRequest filter) {
        PagedResponse<TransactionResponse> response = adminService.listTransactions(filter);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/transactions/{id}/override")
    @Operation(summary = "Force override transaction status")
    public ResponseEntity<ApiResponse<TransactionResponse>> overrideTransactionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OverrideTransactionStatusRequest request) {
        TransactionResponse response = adminService.overrideTransactionStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Transaction status overridden"));
    }

    @GetMapping("/audit-log")
    @Operation(summary = "Query audit log")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogResponse>>> getAuditLog(
            @ModelAttribute AuditLogFilterRequest filter) {
        PagedResponse<AuditLogResponse> response = adminService.getAuditLog(filter);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/transactions/bulk-retry")
    @Operation(summary = "Batch retry failed transactions")
    public ResponseEntity<ApiResponse<BulkRetryResponse>> bulkRetryTransactions(
            @Valid @RequestBody BulkRetryRequest request) {
        BulkRetryResponse response = adminService.bulkRetryTransactions(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Bulk retry completed"));
    }
}
