package com.flowpay.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingRateResponse {

    private List<DataPoint> dataPoints;
    private double currentTps;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private OffsetDateTime timestamp;
        private double tps;
    }
}
