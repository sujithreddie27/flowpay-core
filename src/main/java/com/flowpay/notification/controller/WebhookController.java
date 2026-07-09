package com.flowpay.notification.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.notification.dto.*;
import com.flowpay.notification.service.NotificationPreferenceService;
import com.flowpay.notification.service.NotificationService;
import com.flowpay.notification.service.WebhookConfigService;
import com.flowpay.notification.service.WebhookDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Webhooks", description = "Webhook configuration and delivery management for merchants")
public class WebhookController {

    private final WebhookConfigService webhookConfigService;
    private final WebhookDeliveryService webhookDeliveryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Create webhook config", description = "Create a new webhook configuration for event delivery")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Webhook config created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<ApiResponse<WebhookConfigResponse>> createWebhookConfig(
            @Valid @RequestBody CreateWebhookConfigRequest request) {
        log.info("Creating webhook config for merchant: {}", request.getMerchantId());
        WebhookConfigResponse response = webhookConfigService.createWebhookConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Webhook configuration created successfully"));
    }

    @PatchMapping("/{configId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Update webhook config", description = "Update an existing webhook configuration")
    public ResponseEntity<ApiResponse<WebhookConfigResponse>> updateWebhookConfig(
            @Parameter(description = "Webhook config UUID") @PathVariable UUID configId,
            @Valid @RequestBody UpdateWebhookConfigRequest request) {
        WebhookConfigResponse response = webhookConfigService.updateWebhookConfig(configId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Webhook configuration updated"));
    }

    @GetMapping("/{configId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Get webhook config", description = "Retrieve a webhook configuration by ID")
    public ResponseEntity<ApiResponse<WebhookConfigResponse>> getWebhookConfig(
            @Parameter(description = "Webhook config UUID") @PathVariable UUID configId) {
        WebhookConfigResponse response = webhookConfigService.getWebhookConfig(configId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/merchant/{merchantId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Get webhooks by merchant", description = "Retrieve all webhook configurations for a merchant")
    public ResponseEntity<ApiResponse<List<WebhookConfigResponse>>> getWebhooksByMerchant(
            @Parameter(description = "Merchant UUID") @PathVariable UUID merchantId) {
        List<WebhookConfigResponse> configs = webhookConfigService.getWebhookConfigsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @DeleteMapping("/{configId}")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Delete webhook config", description = "Delete a webhook configuration")
    public ResponseEntity<ApiResponse<Void>> deleteWebhookConfig(
            @Parameter(description = "Webhook config UUID") @PathVariable UUID configId) {
        webhookConfigService.deleteWebhookConfig(configId);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook configuration deleted"));
    }

    @PostMapping("/{configId}/regenerate-secret")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Regenerate webhook secret", description = "Generate a new signing secret for a webhook configuration")
    public ResponseEntity<ApiResponse<String>> regenerateSecret(
            @Parameter(description = "Webhook config UUID") @PathVariable UUID configId) {
        String newSecret = webhookConfigService.regenerateSecret(configId);
        return ResponseEntity.ok(ApiResponse.success(newSecret, "Webhook secret regenerated"));
    }

    @GetMapping("/{configId}/deliveries")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Get webhook deliveries", description = "Retrieve paginated delivery history for a webhook configuration")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryResponse>>> getDeliveries(
            @Parameter(description = "Webhook config UUID") @PathVariable UUID configId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<WebhookDeliveryResponse> deliveries = webhookConfigService.getDeliveries(configId, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(deliveries)));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(summary = "Retry webhook delivery", description = "Retry a failed webhook delivery")
    public ResponseEntity<ApiResponse<Void>> retryDelivery(
            @Parameter(description = "Delivery UUID") @PathVariable UUID deliveryId) {
        webhookDeliveryService.retryDelivery(deliveryId);
        return ResponseEntity.ok(ApiResponse.success(null, "Webhook delivery retry initiated"));
    }
}
