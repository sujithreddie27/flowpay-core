package com.flowpay.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminDashboardStatsResponse {

    private long totalUsers;
    private long activeUsers;
    private long totalTransactions;
    private long pendingTransactions;
    private long failedTransactions;
    private long completedTransactions;
    private BigDecimal totalVolume;
    private BigDecimal totalFees;
}
