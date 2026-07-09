package com.flowpay.notification.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.notification.dto.*;
import com.flowpay.notification.service.NotificationPreferenceService;
import com.flowpay.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification delivery and preference management")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Send a notification", description = "Trigger a notification to a user via configured channels")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Notification sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Notification sent"));
    }

    @GetMapping("/preferences/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Get notification preferences", description = "Retrieve notification preferences for a user")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        NotificationPreferenceResponse response = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/preferences")
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @Operation(summary = "Update notification preferences", description = "Update notification channel preferences for a user")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        NotificationPreferenceResponse response = preferenceService.updatePreferences(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Preferences updated"));
    }

    @PostMapping("/preferences/{userId}/default")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Create default preferences", description = "Create default notification preferences for a user (Admin only)")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> createDefaultPreferences(
            @Parameter(description = "User UUID") @PathVariable UUID userId) {
        NotificationPreferenceResponse response = preferenceService.createDefaultPreferences(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Default preferences created"));
    }
}
