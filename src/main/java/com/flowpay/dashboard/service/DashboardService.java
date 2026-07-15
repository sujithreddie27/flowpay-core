package com.flowpay.dashboard.service;

import com.flowpay.dashboard.dto.DashboardChartsResponse;
import com.flowpay.dashboard.dto.DashboardStatsResponse;
import com.flowpay.dashboard.dto.RealtimeMetricsResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DashboardService {

    DashboardStatsResponse getStats(UUID userId);

    DashboardChartsResponse getCharts(UUID userId, int days);

    List<DashboardChartsResponse.VolumeDataPoint> getTransactionVolume(UUID userId, int days);

    Map<String, Long> getStatusDistribution(UUID userId);

    List<DashboardChartsResponse.RevenueDataPoint> getRevenue(UUID userId, int days);

    RealtimeMetricsResponse getRealtimeMetrics();
}
