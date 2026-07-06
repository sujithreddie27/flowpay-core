package com.flowpay.notification.dto;

import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookConfigRequest {

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Webhook URL is required")
    @URL(message = "Must be a valid URL")
    private String url;

    @NotEmpty(message = "At least one event type is required")
    private Set<WebhookEventType> events;

    private String description;
}
