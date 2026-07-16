package com.flowpay.settings.service;

import com.flowpay.auth.dto.UserResponse;
import com.flowpay.notification.dto.NotificationPreferenceResponse;
import com.flowpay.notification.dto.UpdateNotificationPreferenceRequest;
import com.flowpay.settings.dto.ChangePasswordRequest;
import com.flowpay.settings.dto.TwoFactorSetupResponse;
import com.flowpay.settings.dto.TwoFactorVerifyRequest;
import com.flowpay.settings.dto.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface SettingsService {

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    String uploadAvatar(UUID userId, MultipartFile file);

    void deleteAvatar(UUID userId);

    void changePassword(UUID userId, ChangePasswordRequest request);

    TwoFactorSetupResponse enable2fa(UUID userId);

    void verify2fa(UUID userId, TwoFactorVerifyRequest request);

    NotificationPreferenceResponse getNotificationPreferences(UUID userId);

    NotificationPreferenceResponse updateNotificationPreferences(UUID userId, UpdateNotificationPreferenceRequest request);
}
