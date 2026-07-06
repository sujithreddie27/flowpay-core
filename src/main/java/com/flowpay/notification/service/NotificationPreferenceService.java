package com.flowpay.notification.service;

import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.notification.dto.NotificationPreferenceResponse;
import com.flowpay.notification.dto.UpdateNotificationPreferenceRequest;
import com.flowpay.notification.entity.NotificationPreference;
import com.flowpay.notification.mapper.NotificationMapper;
import com.flowpay.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(UUID userId) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationPreference", userId));
        return notificationMapper.toPreferenceResponse(preference);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(UpdateNotificationPreferenceRequest request) {
        log.info("Updating notification preferences for user: {}", request.getUserId());

        NotificationPreference preference = preferenceRepository.findByUserId(request.getUserId())
                .orElse(NotificationPreference.builder()
                        .userId(request.getUserId())
                        .build());

        preference.setEmailEnabled(request.isEmailEnabled());
        preference.setSmsEnabled(request.isSmsEnabled());
        preference.setPushEnabled(request.isPushEnabled());
        preference.setWebhookEnabled(request.isWebhookEnabled());

        if (request.getEnabledEvents() != null) {
            preference.setEnabledEvents(request.getEnabledEvents());
        }

        preference = preferenceRepository.save(preference);
        return notificationMapper.toPreferenceResponse(preference);
    }

    @Transactional
    public NotificationPreferenceResponse createDefaultPreferences(UUID userId) {
        if (preferenceRepository.existsByUserId(userId)) {
            return getPreferences(userId);
        }

        NotificationPreference preference = NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(true)
                .webhookEnabled(true)
                .build();

        preference = preferenceRepository.save(preference);
        return notificationMapper.toPreferenceResponse(preference);
    }
}
