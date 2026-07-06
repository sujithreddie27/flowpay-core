package com.flowpay.notification.service;

import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.notification.dto.CreateWebhookConfigRequest;
import com.flowpay.notification.dto.UpdateWebhookConfigRequest;
import com.flowpay.notification.dto.WebhookConfigResponse;
import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import com.flowpay.notification.mapper.WebhookMapper;
import com.flowpay.notification.repository.WebhookConfigRepository;
import com.flowpay.notification.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookConfigServiceImpl")
class WebhookConfigServiceImplTest {

    @Mock
    private WebhookConfigRepository configRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookDeliveryService webhookDeliveryService;

    @Mock
    private WebhookMapper webhookMapper;

    @InjectMocks
    private WebhookConfigServiceImpl webhookConfigService;

    private UUID merchantId;
    private UUID configId;
    private WebhookConfig webhookConfig;
    private WebhookConfigResponse configResponse;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        configId = UUID.randomUUID();

        webhookConfig = WebhookConfig.builder()
                .merchantId(merchantId)
                .url("https://merchant.example.com/webhook")
                .secret("test-secret-key")
                .events(Set.of(WebhookEventType.PAYMENT_COMPLETED, WebhookEventType.PAYMENT_FAILED))
                .status(WebhookStatus.ACTIVE)
                .build();
        webhookConfig.setId(configId);

        configResponse = WebhookConfigResponse.builder()
                .id(configId)
                .merchantId(merchantId)
                .url("https://merchant.example.com/webhook")
                .events(Set.of(WebhookEventType.PAYMENT_COMPLETED, WebhookEventType.PAYMENT_FAILED))
                .status(WebhookStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("createWebhookConfig")
    class CreateWebhookConfig {

        @Test
        @DisplayName("should create webhook config successfully")
        void shouldCreateWebhookConfig() {
            CreateWebhookConfigRequest request = CreateWebhookConfigRequest.builder()
                    .merchantId(merchantId)
                    .url("https://merchant.example.com/webhook")
                    .events(Set.of(WebhookEventType.PAYMENT_COMPLETED))
                    .description("Payment notifications")
                    .build();

            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);
            when(webhookMapper.toConfigResponse(any(WebhookConfig.class))).thenReturn(configResponse);

            WebhookConfigResponse response = webhookConfigService.createWebhookConfig(request);

            assertThat(response).isNotNull();
            assertThat(response.getMerchantId()).isEqualTo(merchantId);
            assertThat(response.getUrl()).isEqualTo("https://merchant.example.com/webhook");
            verify(configRepository).save(any(WebhookConfig.class));
        }
    }

    @Nested
    @DisplayName("updateWebhookConfig")
    class UpdateWebhookConfig {

        @Test
        @DisplayName("should update webhook config successfully")
        void shouldUpdateWebhookConfig() {
            UpdateWebhookConfigRequest request = UpdateWebhookConfigRequest.builder()
                    .url("https://merchant.example.com/new-webhook")
                    .status(WebhookStatus.INACTIVE)
                    .build();

            when(configRepository.findById(configId)).thenReturn(Optional.of(webhookConfig));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);
            when(webhookMapper.toConfigResponse(any(WebhookConfig.class))).thenReturn(configResponse);

            WebhookConfigResponse response = webhookConfigService.updateWebhookConfig(configId, request);

            assertThat(response).isNotNull();
            verify(configRepository).save(any(WebhookConfig.class));
        }

        @Test
        @DisplayName("should throw exception when config not found")
        void shouldThrowExceptionWhenConfigNotFound() {
            UpdateWebhookConfigRequest request = UpdateWebhookConfigRequest.builder()
                    .url("https://merchant.example.com/new-webhook")
                    .build();

            when(configRepository.findById(configId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> webhookConfigService.updateWebhookConfig(configId, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getWebhookConfigsByMerchant")
    class GetWebhookConfigsByMerchant {

        @Test
        @DisplayName("should return webhook configs for merchant")
        void shouldReturnWebhookConfigsForMerchant() {
            when(configRepository.findByMerchantId(merchantId)).thenReturn(List.of(webhookConfig));
            when(webhookMapper.toConfigResponse(any(WebhookConfig.class))).thenReturn(configResponse);

            List<WebhookConfigResponse> configs = webhookConfigService.getWebhookConfigsByMerchant(merchantId);

            assertThat(configs).hasSize(1);
            assertThat(configs.get(0).getMerchantId()).isEqualTo(merchantId);
        }
    }

    @Nested
    @DisplayName("deleteWebhookConfig")
    class DeleteWebhookConfig {

        @Test
        @DisplayName("should delete webhook config successfully")
        void shouldDeleteWebhookConfig() {
            when(configRepository.existsById(configId)).thenReturn(true);

            webhookConfigService.deleteWebhookConfig(configId);

            verify(configRepository).deleteById(configId);
        }

        @Test
        @DisplayName("should throw exception when config not found")
        void shouldThrowExceptionWhenConfigNotFound() {
            when(configRepository.existsById(configId)).thenReturn(false);

            assertThatThrownBy(() -> webhookConfigService.deleteWebhookConfig(configId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("triggerWebhooks")
    class TriggerWebhooks {

        @Test
        @DisplayName("should trigger webhooks for subscribed events")
        void shouldTriggerWebhooksForSubscribedEvents() {
            UUID transactionId = UUID.randomUUID();
            Map<String, Object> payload = Map.of("event", "PAYMENT_COMPLETED");

            when(configRepository.findByMerchantIdAndStatus(merchantId, WebhookStatus.ACTIVE))
                    .thenReturn(List.of(webhookConfig));

            webhookConfigService.triggerWebhooks(merchantId, WebhookEventType.PAYMENT_COMPLETED,
                    transactionId, payload);

            verify(webhookDeliveryService).deliverWebhook(
                    eq(webhookConfig), eq(WebhookEventType.PAYMENT_COMPLETED),
                    eq(transactionId), eq(payload));
        }

        @Test
        @DisplayName("should not trigger webhooks for unsubscribed events")
        void shouldNotTriggerWebhooksForUnsubscribedEvents() {
            UUID transactionId = UUID.randomUUID();
            Map<String, Object> payload = Map.of("event", "ACCOUNT_UPDATED");

            when(configRepository.findByMerchantIdAndStatus(merchantId, WebhookStatus.ACTIVE))
                    .thenReturn(List.of(webhookConfig));

            webhookConfigService.triggerWebhooks(merchantId, WebhookEventType.ACCOUNT_UPDATED,
                    transactionId, payload);

            verify(webhookDeliveryService, never()).deliverWebhook(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("regenerateSecret")
    class RegenerateSecret {

        @Test
        @DisplayName("should regenerate secret successfully")
        void shouldRegenerateSecret() {
            when(configRepository.findById(configId)).thenReturn(Optional.of(webhookConfig));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);

            String newSecret = webhookConfigService.regenerateSecret(configId);

            assertThat(newSecret).isNotNull();
            assertThat(newSecret).isNotEmpty();
            assertThat(newSecret).hasSize(64); // 32 bytes hex = 64 chars
            verify(configRepository).save(any(WebhookConfig.class));
        }

        @Test
        @DisplayName("should throw exception when config not found")
        void shouldThrowExceptionWhenConfigNotFound() {
            when(configRepository.findById(configId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> webhookConfigService.regenerateSecret(configId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
