package com.flowpay.monitoring.alerting;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class AlertEvent {

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    public enum AlertType {
        HIGH_ERROR_RATE,
        HIGH_LATENCY,
        DB_POOL_EXHAUSTION,
        POD_RESTART,
        KAFKA_CONSUMER_LAG,
        REDIS_CONNECTION_FAILURE,
        PAYMENT_FAILURE_SPIKE,
        SLO_VIOLATION
    }

    private AlertType alertType;
    private Severity severity;
    private String title;
    private String description;
    private String service;
    private Map<String, String> labels;
    private Instant firedAt;
    private double currentValue;
    private double threshold;
}
