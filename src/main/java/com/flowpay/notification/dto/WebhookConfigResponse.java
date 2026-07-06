package com.flowpay.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookConfigResponse {

    private UUID id;
    private UUID merchantId;
    private String url;
    private Set<WebhookEventType> events;
    private WebhookStatus status;
    private String description;
    private String secret;
    private int failureCount;
    private OffsetDateTime lastTriggeredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
