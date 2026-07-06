package com.flowpay.notification.entity;

import com.flowpay.common.entity.BaseEntity;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "webhook_configs", indexes = {
        @Index(name = "idx_webhook_configs_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_webhook_configs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookConfig extends BaseEntity {

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "secret", nullable = false, length = 128)
    private String secret;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "webhook_config_events",
            joinColumns = @JoinColumn(name = "webhook_config_id")
    )
    @Column(name = "event_type")
    @Enumerated(EnumType.STRING)
    private Set<WebhookEventType> events;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.ACTIVE;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private int failureCount = 0;

    @Column(name = "last_triggered_at")
    private OffsetDateTime lastTriggeredAt;

    public boolean isActive() {
        return this.status == WebhookStatus.ACTIVE;
    }

    public void incrementFailureCount() {
        this.failureCount++;
        if (this.failureCount >= 10) {
            this.status = WebhookStatus.SUSPENDED;
        }
    }

    public void resetFailureCount() {
        this.failureCount = 0;
    }

    public boolean subscribedTo(WebhookEventType eventType) {
        return this.events != null && this.events.contains(eventType);
    }
}
