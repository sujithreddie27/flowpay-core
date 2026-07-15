package com.flowpay.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardChartsResponse {

    private List<VolumeDataPoint> transactionVolume;
    private Map<String, Long> statusDistribution;
    private List<RevenueDataPoint> revenue;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeDataPoint {
        private LocalDate date;
        private long count;
        private BigDecimal amount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueDataPoint {
        private LocalDate date;
        private BigDecimal fees;
    }
}
