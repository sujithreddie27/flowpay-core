package com.flowpay.notification.dto;

import com.flowpay.notification.enums.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationPreferenceRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    private boolean emailEnabled;

    private boolean smsEnabled;

    private boolean pushEnabled;

    private boolean webhookEnabled;

    private Set<String> enabledEvents;
}
