package com.flowpay.dashboard.dto;

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
public class DashboardStatsResponse {

    private long totalTransactions;
    private long pendingTransactions;
    private long completedTransactions;
    private long failedTransactions;
    private long totalAccounts;
    private long activeAccounts;
    private BigDecimal totalBalance;
    private BigDecimal totalVolume;
    private BigDecimal totalRevenue;
}
