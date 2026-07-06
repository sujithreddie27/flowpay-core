package com.flowpay.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookDeliveryResponse {

    private UUID id;
    private UUID webhookConfigId;
    private UUID transactionId;
    private String eventType;
    private String url;
    private int httpStatus;
    private int attemptCount;
    private int maxAttempts;
    private String responseBody;
    private String failureReason;
    private boolean successful;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime createdAt;
}
