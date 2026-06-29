package com.flowpay.monitoring.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Value("${flowpay.payment-gateway.health-url:#{null}}")
    private String healthUrl;

    @Value("${flowpay.payment-gateway.name:External Payment Gateway}")
    private String gatewayName;

    private final HttpClient httpClient;

    public PaymentGatewayHealthIndicator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public Health health() {
        if (healthUrl == null || healthUrl.isBlank()) {
            return Health.unknown()
                    .withDetail("service", gatewayName)
                    .withDetail("reason", "Health URL not configured")
                    .build();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = System.currentTimeMillis() - start;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Health.up()
                        .withDetail("service", gatewayName)
                        .withDetail("url", healthUrl)
                        .withDetail("statusCode", response.statusCode())
                        .withDetail("latencyMs", latencyMs)
                        .build();
            }

            return Health.down()
                    .withDetail("service", gatewayName)
                    .withDetail("url", healthUrl)
                    .withDetail("statusCode", response.statusCode())
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (Exception e) {
            log.error("Payment gateway health check failed for {}", gatewayName, e);
            return Health.down()
                    .withDetail("service", gatewayName)
                    .withDetail("url", healthUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
