package com.flowpay.notification.entity;

import com.flowpay.common.entity.BaseEntity;
import com.flowpay.notification.enums.NotificationStatus;
import com.flowpay.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_status", columnList = "status"),
        @Index(name = "idx_notifications_type", columnList = "type"),
        @Index(name = "idx_notifications_transaction_id", columnList = "transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "content", nullable = false, length = 10000)
    private String content;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = OffsetDateTime.now();
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRetrying() {
        this.status = NotificationStatus.RETRYING;
        this.retryCount++;
    }
}
