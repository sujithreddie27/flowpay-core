package com.flowpay.dashboard.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.dashboard.dto.DashboardChartsResponse;
import com.flowpay.dashboard.dto.DashboardStatsResponse;
import com.flowpay.dashboard.dto.RealtimeMetricsResponse;
import com.flowpay.dashboard.service.DashboardService;
import com.flowpay.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
@Tag(name = "Dashboard", description = "Dashboard analytics and metrics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard stats", description = "Aggregate statistics for the current user")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        DashboardStatsResponse response = dashboardService.getStats(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/charts")
    @Operation(summary = "Get chart data", description = "Combined chart data for dashboard visualizations")
    public ResponseEntity<ApiResponse<DashboardChartsResponse>> getCharts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "30") int days) {
        DashboardChartsResponse response = dashboardService.getCharts(userDetails.getUserId(), days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transaction-volume")
    @Operation(summary = "Get transaction volume", description = "Transaction volume grouped by date")
    public ResponseEntity<ApiResponse<List<DashboardChartsResponse.VolumeDataPoint>>> getTransactionVolume(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "30") int days) {
        List<DashboardChartsResponse.VolumeDataPoint> response =
                dashboardService.getTransactionVolume(userDetails.getUserId(), days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/status-distribution")
    @Operation(summary = "Get status distribution", description = "Transaction count by status")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusDistribution(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Long> response = dashboardService.getStatusDistribution(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get revenue", description = "Sum of transaction fees by day")
    public ResponseEntity<ApiResponse<List<DashboardChartsResponse.RevenueDataPoint>>> getRevenue(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "30") int days) {
        List<DashboardChartsResponse.RevenueDataPoint> response =
                dashboardService.getRevenue(userDetails.getUserId(), days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/realtime")
    @Operation(summary = "Get realtime metrics", description = "Live system metrics")
    public ResponseEntity<ApiResponse<RealtimeMetricsResponse>> getRealtimeMetrics() {
        RealtimeMetricsResponse response = dashboardService.getRealtimeMetrics();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
