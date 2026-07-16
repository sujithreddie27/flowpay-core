package com.flowpay.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.monitoring.alerting.AlertEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertResponse {

    private String alertType;
    private String severity;
    private String title;
    private String description;
    private String service;
    private Instant firedAt;
    private double currentValue;
    private double threshold;

    public static AlertResponse from(AlertEvent event) {
        return AlertResponse.builder()
                .alertType(event.getAlertType().name())
                .severity(event.getSeverity().name())
                .title(event.getTitle())
                .description(event.getDescription())
                .service(event.getService())
                .firedAt(event.getFiredAt())
                .currentValue(event.getCurrentValue())
                .threshold(event.getThreshold())
                .build();
    }
}
