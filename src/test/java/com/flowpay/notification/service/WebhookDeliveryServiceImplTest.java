package com.flowpay.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.entity.WebhookDelivery;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import com.flowpay.notification.repository.WebhookConfigRepository;
import com.flowpay.notification.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDeliveryServiceImpl")
class WebhookDeliveryServiceImplTest {

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookConfigRepository configRepository;

    @Mock
    private RestTemplate webhookRestTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookDeliveryServiceImpl webhookDeliveryService;

    private WebhookConfig webhookConfig;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();

        webhookConfig = WebhookConfig.builder()
                .merchantId(UUID.randomUUID())
                .url("https://merchant.example.com/webhook")
                .secret("test-secret")
                .events(Set.of(WebhookEventType.PAYMENT_COMPLETED))
                .status(WebhookStatus.ACTIVE)
                .build();
        webhookConfig.setId(UUID.randomUUID());
    }

    @Nested
    @DisplayName("deliverWebhook")
    class DeliverWebhook {

        @Test
        @DisplayName("should deliver webhook successfully")
        void shouldDeliverWebhookSuccessfully() throws Exception {
            Map<String, Object> payload = Map.of("event", "PAYMENT_COMPLETED", "transactionId", transactionId.toString());
            String serializedPayload = "{\"event\":\"PAYMENT_COMPLETED\"}";

            WebhookDelivery savedDelivery = WebhookDelivery.builder()
                    .webhookConfig(webhookConfig)
                    .transactionId(transactionId)
                    .eventType("PAYMENT_COMPLETED")
                    .url(webhookConfig.getUrl())
                    .requestBody(serializedPayload)
                    .build();
            savedDelivery.setId(UUID.randomUUID());

            when(objectMapper.writeValueAsString(payload)).thenReturn(serializedPayload);
            when(deliveryRepository.save(any(WebhookDelivery.class))).thenReturn(savedDelivery);
            when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);

            webhookDeliveryService.deliverWebhook(webhookConfig, WebhookEventType.PAYMENT_COMPLETED,
                    transactionId, payload);

            verify(deliveryRepository, times(2)).save(any(WebhookDelivery.class));
            verify(configRepository).save(any(WebhookConfig.class));
        }

        @Test
        @DisplayName("should mark delivery as failed on connection error")
        void shouldMarkDeliveryAsFailedOnConnectionError() throws Exception {
            Map<String, Object> payload = Map.of("event", "PAYMENT_COMPLETED");
            String serializedPayload = "{\"event\":\"PAYMENT_COMPLETED\"}";

            WebhookDelivery savedDelivery = WebhookDelivery.builder()
                    .webhookConfig(webhookConfig)
                    .transactionId(transactionId)
                    .eventType("PAYMENT_COMPLETED")
                    .url(webhookConfig.getUrl())
                    .requestBody(serializedPayload)
                    .build();
            savedDelivery.setId(UUID.randomUUID());

            when(objectMapper.writeValueAsString(payload)).thenReturn(serializedPayload);
            when(deliveryRepository.save(any(WebhookDelivery.class))).thenReturn(savedDelivery);
            when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);

            webhookDeliveryService.deliverWebhook(webhookConfig, WebhookEventType.PAYMENT_COMPLETED,
                    transactionId, payload);

            ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
            verify(deliveryRepository, times(2)).save(captor.capture());
            WebhookDelivery lastSaved = captor.getAllValues().get(1);
            assertThat(lastSaved.isSuccessful()).isFalse();
        }

        @Test
        @DisplayName("should mark delivery as failed on non-2xx response")
        void shouldMarkDeliveryAsFailedOnNon2xxResponse() throws Exception {
            Map<String, Object> payload = Map.of("event", "PAYMENT_COMPLETED");
            String serializedPayload = "{\"event\":\"PAYMENT_COMPLETED\"}";

            WebhookDelivery savedDelivery = WebhookDelivery.builder()
                    .webhookConfig(webhookConfig)
                    .transactionId(transactionId)
                    .eventType("PAYMENT_COMPLETED")
                    .url(webhookConfig.getUrl())
                    .requestBody(serializedPayload)
                    .build();
            savedDelivery.setId(UUID.randomUUID());

            when(objectMapper.writeValueAsString(payload)).thenReturn(serializedPayload);
            when(deliveryRepository.save(any(WebhookDelivery.class))).thenReturn(savedDelivery);
            when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);

            webhookDeliveryService.deliverWebhook(webhookConfig, WebhookEventType.PAYMENT_COMPLETED,
                    transactionId, payload);

            verify(deliveryRepository, times(2)).save(any(WebhookDelivery.class));
        }

        @Test
        @DisplayName("should include HMAC signature header")
        void shouldIncludeHmacSignatureHeader() throws Exception {
            Map<String, Object> payload = Map.of("event", "PAYMENT_COMPLETED");
            String serializedPayload = "{\"event\":\"PAYMENT_COMPLETED\"}";

            WebhookDelivery savedDelivery = WebhookDelivery.builder()
                    .webhookConfig(webhookConfig)
                    .transactionId(transactionId)
                    .eventType("PAYMENT_COMPLETED")
                    .url(webhookConfig.getUrl())
                    .requestBody(serializedPayload)
                    .build();
            savedDelivery.setId(UUID.randomUUID());

            when(objectMapper.writeValueAsString(payload)).thenReturn(serializedPayload);
            when(deliveryRepository.save(any(WebhookDelivery.class))).thenReturn(savedDelivery);
            when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);

            webhookDeliveryService.deliverWebhook(webhookConfig, WebhookEventType.PAYMENT_COMPLETED,
                    transactionId, payload);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<String>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(webhookRestTemplate).exchange(anyString(), eq(HttpMethod.POST), httpEntityCaptor.capture(), eq(String.class));

            HttpEntity<String> capturedEntity = httpEntityCaptor.getValue();
            assertThat(capturedEntity.getHeaders().get("X-FlowPay-Signature")).isNotNull();
            assertThat(capturedEntity.getHeaders().getFirst("X-FlowPay-Signature")).startsWith("sha256=");
            assertThat(capturedEntity.getHeaders().getFirst("X-FlowPay-Event")).isEqualTo("PAYMENT_COMPLETED");
        }
    }

    @Nested
    @DisplayName("retryFailedDeliveries")
    class RetryFailedDeliveries {

        @Test
        @DisplayName("should retry pending deliveries")
        void shouldRetryPendingDeliveries() {
            WebhookDelivery delivery = WebhookDelivery.builder()
                    .webhookConfig(webhookConfig)
                    .transactionId(transactionId)
                    .eventType("PAYMENT_COMPLETED")
                    .url(webhookConfig.getUrl())
                    .requestBody("{\"event\":\"PAYMENT_COMPLETED\"}")
                    .attemptCount(1)
                    .maxAttempts(5)
                    .nextRetryAt(OffsetDateTime.now().minusMinutes(1))
                    .build();
            delivery.setId(UUID.randomUUID());

            when(deliveryRepository.findPendingRetries(any(OffsetDateTime.class)))
                    .thenReturn(List.of(delivery));
            when(webhookRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
            when(configRepository.save(any(WebhookConfig.class))).thenReturn(webhookConfig);
            when(deliveryRepository.save(any(WebhookDelivery.class))).thenReturn(delivery);

            webhookDeliveryService.retryFailedDeliveries();

            verify(webhookRestTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
            verify(deliveryRepository).save(any(WebhookDelivery.class));
        }

        @Test
        @DisplayName("should do nothing when no pending retries")
        void shouldDoNothingWhenNoPendingRetries() {
            when(deliveryRepository.findPendingRetries(any(OffsetDateTime.class)))
                    .thenReturn(Collections.emptyList());

            webhookDeliveryService.retryFailedDeliveries();

            verify(webhookRestTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), any(Class.class));
        }
    }
}
