package com.flowpay.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.notification.dto.*;
import com.flowpay.notification.enums.WebhookEventType;
import com.flowpay.notification.enums.WebhookStatus;
import com.flowpay.notification.service.WebhookConfigService;
import com.flowpay.notification.service.WebhookDeliveryService;
import com.flowpay.security.JwtTokenProvider;
import com.flowpay.security.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
@DisplayName("WebhookController")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookConfigService webhookConfigService;

    @MockBean
    private WebhookDeliveryService webhookDeliveryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Nested
    @DisplayName("POST /api/v1/webhooks")
    class CreateWebhook {

        @Test
        @DisplayName("should create webhook config successfully")
        void shouldCreateWebhookConfig() throws Exception {
            UUID merchantId = UUID.randomUUID();

            CreateWebhookConfigRequest request = CreateWebhookConfigRequest.builder()
                    .merchantId(merchantId)
                    .url("https://merchant.example.com/webhook")
                    .events(Set.of(WebhookEventType.PAYMENT_COMPLETED))
                    .description("Payment notifications")
                    .build();

            WebhookConfigResponse response = WebhookConfigResponse.builder()
                    .id(UUID.randomUUID())
                    .merchantId(merchantId)
                    .url("https://merchant.example.com/webhook")
                    .events(Set.of(WebhookEventType.PAYMENT_COMPLETED))
                    .status(WebhookStatus.ACTIVE)
                    .build();

            when(webhookConfigService.createWebhookConfig(any())).thenReturn(response);

            mockMvc.perform(post("/api/v1/webhooks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.merchantId").value(merchantId.toString()));
        }

        @Test
        @DisplayName("should return 400 for invalid request")
        void shouldReturn400ForInvalidRequest() throws Exception {
            CreateWebhookConfigRequest request = CreateWebhookConfigRequest.builder()
                    .merchantId(null)
                    .url("")
                    .events(Set.of())
                    .build();

            mockMvc.perform(post("/api/v1/webhooks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/webhooks/merchant/{merchantId}")
    class GetWebhooksByMerchant {

        @Test
        @DisplayName("should return webhook configs for merchant")
        void shouldReturnWebhookConfigsForMerchant() throws Exception {
            UUID merchantId = UUID.randomUUID();

            WebhookConfigResponse response = WebhookConfigResponse.builder()
                    .id(UUID.randomUUID())
                    .merchantId(merchantId)
                    .url("https://merchant.example.com/webhook")
                    .events(Set.of(WebhookEventType.PAYMENT_COMPLETED))
                    .status(WebhookStatus.ACTIVE)
                    .build();

            when(webhookConfigService.getWebhookConfigsByMerchant(merchantId))
                    .thenReturn(List.of(response));

            mockMvc.perform(get("/api/v1/webhooks/merchant/{merchantId}", merchantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].merchantId").value(merchantId.toString()));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/webhooks/{configId}")
    class DeleteWebhook {

        @Test
        @DisplayName("should delete webhook config")
        void shouldDeleteWebhookConfig() throws Exception {
            UUID configId = UUID.randomUUID();

            mockMvc.perform(delete("/api/v1/webhooks/{configId}", configId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
