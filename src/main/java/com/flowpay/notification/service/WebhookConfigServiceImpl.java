package com.flowpay.notification.service;

import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.notification.dto.*;
import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.entity.WebhookDelivery;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import com.flowpay.notification.mapper.WebhookMapper;
import com.flowpay.notification.repository.WebhookConfigRepository;
import com.flowpay.notification.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookConfigServiceImpl implements WebhookConfigService {

    private final WebhookConfigRepository configRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final WebhookMapper webhookMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional
    public WebhookConfigResponse createWebhookConfig(CreateWebhookConfigRequest request) {
        log.info("Creating webhook config for merchant: {}", request.getMerchantId());

        WebhookConfig config = WebhookConfig.builder()
                .merchantId(request.getMerchantId())
                .url(request.getUrl())
                .secret(generateSecret())
                .events(request.getEvents())
                .description(request.getDescription())
                .status(WebhookStatus.ACTIVE)
                .build();

        config = configRepository.save(config);
        log.info("Webhook config created: id={}", config.getId());

        return webhookMapper.toConfigResponse(config);
    }

    @Override
    @Transactional
    public WebhookConfigResponse updateWebhookConfig(UUID configId, UpdateWebhookConfigRequest request) {
        WebhookConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookConfig", configId));

        if (request.getUrl() != null) {
            config.setUrl(request.getUrl());
        }
        if (request.getEvents() != null) {
            config.setEvents(request.getEvents());
        }
        if (request.getStatus() != null) {
            config.setStatus(request.getStatus());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }

        config = configRepository.save(config);
        return webhookMapper.toConfigResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookConfigResponse getWebhookConfig(UUID configId) {
        WebhookConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookConfig", configId));
        return webhookMapper.toConfigResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookConfigResponse> getWebhookConfigsByMerchant(UUID merchantId) {
        return configRepository.findByMerchantId(merchantId).stream()
                .map(webhookMapper::toConfigResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteWebhookConfig(UUID configId) {
        if (!configRepository.existsById(configId)) {
            throw new ResourceNotFoundException("WebhookConfig", configId);
        }
        configRepository.deleteById(configId);
        log.info("Webhook config deleted: id={}", configId);
    }

    @Override
    @Transactional
    public String regenerateSecret(UUID configId) {
        WebhookConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookConfig", configId));

        String newSecret = generateSecret();
        config.setSecret(newSecret);
        configRepository.save(config);

        log.info("Webhook secret regenerated: configId={}", configId);
        return newSecret;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> getDeliveries(UUID configId, Pageable pageable) {
        if (!configRepository.existsById(configId)) {
            throw new ResourceNotFoundException("WebhookConfig", configId);
        }
        return deliveryRepository.findByWebhookConfigId(configId, pageable)
                .map(webhookMapper::toDeliveryResponse);
    }

    @Override
    @Transactional
    public void triggerWebhooks(UUID merchantId, WebhookEventType eventType,
                                UUID transactionId, Map<String, Object> payload) {
        List<WebhookConfig> configs = configRepository.findByMerchantIdAndStatus(
                merchantId, WebhookStatus.ACTIVE);

        for (WebhookConfig config : configs) {
            if (config.subscribedTo(eventType)) {
                webhookDeliveryService.deliverWebhook(config, eventType, transactionId, payload);
            }
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
