package com.flowpay.notification.service;

import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.notification.dto.NotificationPreferenceResponse;
import com.flowpay.notification.dto.UpdateNotificationPreferenceRequest;
import com.flowpay.notification.entity.NotificationPreference;
import com.flowpay.notification.mapper.NotificationMapper;
import com.flowpay.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPreferenceService")
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationPreferenceService preferenceService;

    private UUID userId;
    private NotificationPreference preference;
    private NotificationPreferenceResponse preferenceResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        preference = NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(true)
                .webhookEnabled(true)
                .build();
        preference.setId(UUID.randomUUID());

        preferenceResponse = NotificationPreferenceResponse.builder()
                .id(preference.getId())
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(false)
                .pushEnabled(true)
                .webhookEnabled(true)
                .build();
    }

    @Nested
    @DisplayName("getPreferences")
    class GetPreferences {

        @Test
        @DisplayName("should return preferences for user")
        void shouldReturnPreferencesForUser() {
            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(notificationMapper.toPreferenceResponse(preference)).thenReturn(preferenceResponse);

            NotificationPreferenceResponse response = preferenceService.getPreferences(userId);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(userId);
            assertThat(response.isEmailEnabled()).isTrue();
        }

        @Test
        @DisplayName("should throw exception when preferences not found")
        void shouldThrowExceptionWhenPreferencesNotFound() {
            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> preferenceService.getPreferences(userId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updatePreferences")
    class UpdatePreferences {

        @Test
        @DisplayName("should update existing preferences")
        void shouldUpdateExistingPreferences() {
            UpdateNotificationPreferenceRequest request = UpdateNotificationPreferenceRequest.builder()
                    .userId(userId)
                    .emailEnabled(false)
                    .smsEnabled(true)
                    .pushEnabled(true)
                    .webhookEnabled(false)
                    .enabledEvents(Set.of("PAYMENT_COMPLETED"))
                    .build();

            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(preference);
            when(notificationMapper.toPreferenceResponse(any(NotificationPreference.class)))
                    .thenReturn(preferenceResponse);

            NotificationPreferenceResponse response = preferenceService.updatePreferences(request);

            assertThat(response).isNotNull();
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }

        @Test
        @DisplayName("should create preferences if none exist")
        void shouldCreatePreferencesIfNoneExist() {
            UpdateNotificationPreferenceRequest request = UpdateNotificationPreferenceRequest.builder()
                    .userId(userId)
                    .emailEnabled(true)
                    .smsEnabled(false)
                    .pushEnabled(true)
                    .webhookEnabled(true)
                    .build();

            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(preference);
            when(notificationMapper.toPreferenceResponse(any(NotificationPreference.class)))
                    .thenReturn(preferenceResponse);

            NotificationPreferenceResponse response = preferenceService.updatePreferences(request);

            assertThat(response).isNotNull();
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }
    }

    @Nested
    @DisplayName("createDefaultPreferences")
    class CreateDefaultPreferences {

        @Test
        @DisplayName("should create default preferences for new user")
        void shouldCreateDefaultPreferencesForNewUser() {
            when(preferenceRepository.existsByUserId(userId)).thenReturn(false);
            when(preferenceRepository.save(any(NotificationPreference.class))).thenReturn(preference);
            when(notificationMapper.toPreferenceResponse(any(NotificationPreference.class)))
                    .thenReturn(preferenceResponse);

            NotificationPreferenceResponse response = preferenceService.createDefaultPreferences(userId);

            assertThat(response).isNotNull();
            verify(preferenceRepository).save(any(NotificationPreference.class));
        }

        @Test
        @DisplayName("should return existing preferences if already exist")
        void shouldReturnExistingPreferencesIfAlreadyExist() {
            when(preferenceRepository.existsByUserId(userId)).thenReturn(true);
            when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
            when(notificationMapper.toPreferenceResponse(preference)).thenReturn(preferenceResponse);

            NotificationPreferenceResponse response = preferenceService.createDefaultPreferences(userId);

            assertThat(response).isNotNull();
            verify(preferenceRepository, never()).save(any());
        }
    }
}
