package com.flowpay.settings.controller;

import com.flowpay.auth.dto.UserResponse;
import com.flowpay.common.dto.ApiResponse;
import com.flowpay.notification.dto.NotificationPreferenceResponse;
import com.flowpay.notification.dto.UpdateNotificationPreferenceRequest;
import com.flowpay.security.CustomUserDetails;
import com.flowpay.settings.dto.ChangePasswordRequest;
import com.flowpay.settings.dto.TwoFactorSetupResponse;
import com.flowpay.settings.dto.TwoFactorVerifyRequest;
import com.flowpay.settings.dto.UpdateProfileRequest;
import com.flowpay.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
@Tag(name = "Settings", description = "User settings and preferences management")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = settingsService.getProfile(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update profile", description = "Update name and phone")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = settingsService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar", description = "Upload a profile avatar image")
    public ResponseEntity<ApiResponse<String>> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        String avatarPath = settingsService.uploadAvatar(userDetails.getUserId(), file);
        return ResponseEntity.ok(ApiResponse.success(avatarPath, "Avatar uploaded successfully"));
    }

    @DeleteMapping("/profile/avatar")
    @Operation(summary = "Delete avatar", description = "Remove profile avatar")
    public ResponseEntity<ApiResponse<Void>> deleteAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        settingsService.deleteAvatar(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Avatar deleted successfully"));
    }

    @PostMapping("/security/password")
    @Operation(summary = "Change password", description = "Validate old password and set new one")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        settingsService.changePassword(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    @PostMapping("/security/2fa/enable")
    @Operation(summary = "Enable 2FA", description = "Generate TOTP secret and QR code URI")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> enable2fa(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TwoFactorSetupResponse response = settingsService.enable2fa(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "2FA setup initiated"));
    }

    @PostMapping("/security/2fa/verify")
    @Operation(summary = "Verify 2FA", description = "Verify TOTP code and activate 2FA")
    public ResponseEntity<ApiResponse<Void>> verify2fa(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TwoFactorVerifyRequest request) {
        settingsService.verify2fa(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "2FA activated successfully"));
    }

    @GetMapping("/notifications")
    @Operation(summary = "Get notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getNotificationPreferences(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        NotificationPreferenceResponse response = settingsService.getNotificationPreferences(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/notifications")
    @Operation(summary = "Update notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updateNotificationPreferences(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        NotificationPreferenceResponse response = settingsService.updateNotificationPreferences(
                userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Notification preferences updated"));
    }
}
