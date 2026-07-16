package com.flowpay.admin.service;

import com.flowpay.admin.dto.*;
import com.flowpay.auth.dto.UserResponse;
import com.flowpay.common.dto.AuditLogFilterRequest;
import com.flowpay.common.dto.AuditLogResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.dto.TransactionResponse;

import java.util.UUID;

public interface AdminService {

    AdminDashboardStatsResponse getDashboardStats();

    ProcessingRateResponse getProcessingRate();

    LatencyResponse getLatency();

    PagedResponse<UserResponse> listUsers(int page, int size);

    AdminUserDetailResponse getUserDetail(UUID userId);

    UserResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request);

    PagedResponse<TransactionResponse> listTransactions(TransactionFilterRequest filter);

    TransactionResponse overrideTransactionStatus(UUID transactionId, OverrideTransactionStatusRequest request);

    PagedResponse<AuditLogResponse> getAuditLog(AuditLogFilterRequest filter);

    BulkRetryResponse bulkRetryTransactions(BulkRetryRequest request);
}
