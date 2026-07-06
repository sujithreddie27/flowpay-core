package com.flowpay.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.notification.enums.NotificationStatus;
import com.flowpay.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private NotificationType type;
    private NotificationStatus status;
    private String subject;
    private String recipientEmail;
    private UUID transactionId;
    private int retryCount;
    private String failureReason;
    private OffsetDateTime sentAt;
    private OffsetDateTime createdAt;
}
