package com.flowpay.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.entity.WebhookDelivery;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.repository.WebhookConfigRepository;
import com.flowpay.notification.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDeliveryServiceImpl implements WebhookDeliveryService {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookConfigRepository configRepository;
    private final RestTemplate webhookRestTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Async("notificationExecutor")
    public void deliverWebhook(WebhookConfig config, WebhookEventType eventType,
                               UUID transactionId, Map<String, Object> payload) {
        log.info("Delivering webhook: configId={}, event={}, transactionId={}",
                config.getId(), eventType, transactionId);

        String requestBody = serializePayload(payload);

        WebhookDelivery delivery = WebhookDelivery.builder()
                .webhookConfig(config)
                .transactionId(transactionId)
                .eventType(eventType.name())
                .url(config.getUrl())
                .requestBody(requestBody)
                .build();

        delivery = deliveryRepository.save(delivery);

        executeDelivery(delivery, config.getSecret(), requestBody);
    }

    @Override
    @Scheduled(fixedDelayString = "${flowpay.notification.webhook.retry-interval-ms:60000}")
    @Transactional
    public void retryFailedDeliveries() {
        List<WebhookDelivery> pendingRetries = deliveryRepository.findPendingRetries(OffsetDateTime.now());

        if (pendingRetries.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed webhook deliveries", pendingRetries.size());

        for (WebhookDelivery delivery : pendingRetries) {
            try {
                executeDelivery(delivery, delivery.getWebhookConfig().getSecret(), delivery.getRequestBody());
            } catch (Exception e) {
                log.error("Error retrying webhook delivery: deliveryId={}, error={}",
                        delivery.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void retryDelivery(UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        if (!delivery.canRetry()) {
            throw new IllegalStateException("Delivery cannot be retried: " + deliveryId);
        }

        executeDelivery(delivery, delivery.getWebhookConfig().getSecret(), delivery.getRequestBody());
    }

    private void executeDelivery(WebhookDelivery delivery, String secret, String requestBody) {
        String signature = WebhookSignatureUtil.generateSignature(requestBody, secret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-FlowPay-Signature", signature);
        headers.set("X-FlowPay-Event", delivery.getEventType());
        headers.set("X-FlowPay-Delivery-Id", delivery.getId().toString());
        headers.set("X-FlowPay-Timestamp", String.valueOf(System.currentTimeMillis()));

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = webhookRestTemplate.exchange(
                    delivery.getUrl(), HttpMethod.POST, requestEntity, String.class);

            int statusCode = response.getStatusCode().value();

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.markSuccessful(statusCode, response.getBody());
                delivery.getWebhookConfig().resetFailureCount();
                delivery.getWebhookConfig().setLastTriggeredAt(OffsetDateTime.now());
                configRepository.save(delivery.getWebhookConfig());
                log.info("Webhook delivered successfully: deliveryId={}, status={}",
                        delivery.getId(), statusCode);
            } else {
                delivery.markFailed(statusCode, response.getBody(),
                        "Non-2xx response: " + statusCode);
                delivery.getWebhookConfig().incrementFailureCount();
                configRepository.save(delivery.getWebhookConfig());
                log.warn("Webhook delivery failed: deliveryId={}, status={}",
                        delivery.getId(), statusCode);
            }
        } catch (RestClientException e) {
            delivery.markFailed("Connection error: " + e.getMessage());
            delivery.getWebhookConfig().incrementFailureCount();
            configRepository.save(delivery.getWebhookConfig());
            log.error("Webhook delivery connection error: deliveryId={}, error={}",
                    delivery.getId(), e.getMessage());
        }

        deliveryRepository.save(delivery);
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize webhook payload", e);
        }
    }
}
