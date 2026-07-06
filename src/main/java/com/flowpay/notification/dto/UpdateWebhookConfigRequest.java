package com.flowpay.notification.dto;

import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
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
public class UpdateWebhookConfigRequest {

    @URL(message = "Must be a valid URL")
    private String url;

    private Set<WebhookEventType> events;

    private WebhookStatus status;

    private String description;
}
