package com.flowpay.monitoring.dto;

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
public class ResponseTimesResponse {

    private double p50Ms;
    private double p95Ms;
    private double p99Ms;
    private double meanMs;
    private double maxMs;
    private long totalRequests;
}
