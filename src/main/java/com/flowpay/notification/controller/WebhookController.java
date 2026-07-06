package com.flowpay.notification.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.notification.dto.*;
import com.flowpay.notification.service.NotificationPreferenceService;
import com.flowpay.notification.service.NotificationService;
import com.flowpay.notification.service.WebhookConfigService;
import com.flowpay.notification.service.WebhookDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookConfigService webhookConfigService;
    private final WebhookDeliveryService webhookDeliveryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<WebhookConfigResponse>> createWebhookConfig(
            @Valid @RequestBody CreateWebhookConfigRequest request) {
        log.info("Creating webhook config for merchant: {}", request.getMerchantId());
        WebhookConfigResponse response = webhookConfigService.createWebhookConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Webhook configuration created successfully"));
    }

    @PatchMapping("/{configId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<WebhookConfigResponse>> updateWebhookConfig(
            @PathVariable UUID configId,
            @Valid @RequestBody UpdateWebhookConfigRequest request) {
        WebhookConfigResponse response = webhookConfigService.updateWebhookConfig(configId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Webhook configuration updated"));
    }

    @GetMapping("/{configId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<WebhookConfigResponse>> getWebhookConfig(
            @PathVariable UUID configId) {
        WebhookConfigResponse response = webhookConfigService.getWebhookConfig(configId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<WebhookConfigResponse>>> getWebhooksByMerchant(
            @PathVariable UUID merchantId) {
        List<WebhookConfigResponse> configs = webhookConfigService.getWebhookConfigsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @DeleteMapping("/{configId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteWebhookConfig(@PathVariable UUID configId) {
        webhookConfigService.deleteWebhookConfig(configId);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook configuration deleted"));
    }

    @PostMapping("/{configId}/regenerate-secret")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> regenerateSecret(@PathVariable UUID configId) {
        String newSecret = webhookConfigService.regenerateSecret(configId);
        return ResponseEntity.ok(ApiResponse.success(newSecret, "Webhook secret regenerated"));
    }

    @GetMapping("/{configId}/deliveries")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryResponse>>> getDeliveries(
            @PathVariable UUID configId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<WebhookDeliveryResponse> deliveries = webhookConfigService.getDeliveries(configId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(deliveries)));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> retryDelivery(@PathVariable UUID deliveryId) {
        webhookDeliveryService.retryDelivery(deliveryId);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook delivery retry initiated"));
    }
}
