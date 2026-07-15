package com.flowpay.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeMetricsResponse {

    private long activeUsers;
    private long transactionsPerMinute;
    private double averageResponseTime;
    private long pendingQueue;
    private double systemCpuUsage;
    private long memoryUsedMb;
    private long uptimeSeconds;
}
