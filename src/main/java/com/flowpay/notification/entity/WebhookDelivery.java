package com.flowpay.notification.entity;

import com.flowpay.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries", indexes = {
        @Index(name = "idx_webhook_deliveries_config_id", columnList = "webhook_config_id"),
        @Index(name = "idx_webhook_deliveries_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_webhook_deliveries_status", columnList = "successful"),
        @Index(name = "idx_webhook_deliveries_next_retry", columnList = "next_retry_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_config_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_webhook_delivery_config"))
    private WebhookConfig webhookConfig;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "successful", nullable = false)
    @Builder.Default
    private boolean successful = false;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    public void markSuccessful(int statusCode, String responseBody) {
        this.successful = true;
        this.httpStatus = statusCode;
        this.responseBody = responseBody;
        this.deliveredAt = OffsetDateTime.now();
        this.nextRetryAt = null;
    }

    public void markFailed(int statusCode, String responseBody, String reason) {
        this.httpStatus = statusCode;
        this.responseBody = responseBody;
        this.failureReason = reason;
        this.attemptCount++;
        if (this.attemptCount < this.maxAttempts) {
            this.nextRetryAt = calculateNextRetry();
        }
    }

    public void markFailed(String reason) {
        this.failureReason = reason;
        this.attemptCount++;
        if (this.attemptCount < this.maxAttempts) {
            this.nextRetryAt = calculateNextRetry();
        }
    }

    public boolean canRetry() {
        return !this.successful && this.attemptCount < this.maxAttempts;
    }

    private OffsetDateTime calculateNextRetry() {
        long delaySeconds = (long) Math.pow(2, this.attemptCount) * 30;
        return OffsetDateTime.now().plusSeconds(delaySeconds);
    }
}
