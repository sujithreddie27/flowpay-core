package com.flowpay.notification.entity;

import com.flowpay.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences", indexes = {
        @Index(name = "idx_notification_prefs_user_id", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private boolean smsEnabled = false;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "webhook_enabled", nullable = false)
    @Builder.Default
    private boolean webhookEnabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "notification_preference_events",
            joinColumns = @JoinColumn(name = "preference_id")
    )
    @Column(name = "event_type")
    @Builder.Default
    private Set<String> enabledEvents = Set.of(
            "PAYMENT_COMPLETED", "PAYMENT_FAILED", "PAYMENT_REVERSED"
    );

    public boolean isEventEnabled(String eventType) {
        return enabledEvents != null && enabledEvents.contains(eventType);
    }
}
