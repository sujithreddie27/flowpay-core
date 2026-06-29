package com.flowpay.monitoring.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentGatewayHealthIndicator Tests")
class PaymentGatewayHealthIndicatorTest {

    @Nested
    @DisplayName("When health URL is not configured")
    class NoHealthUrl {

        @Test
        @DisplayName("should return UNKNOWN when URL is not set")
        void shouldReturnUnknownWhenUrlIsNull() {
            PaymentGatewayHealthIndicator indicator = createIndicator(null, "Test Gateway");

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
            assertThat(health.getDetails()).containsEntry("service", "Test Gateway");
            assertThat(health.getDetails()).containsEntry("reason", "Health URL not configured");
        }

        @Test
        @DisplayName("should return UNKNOWN when URL is blank")
        void shouldReturnUnknownWhenUrlIsBlank() {
            PaymentGatewayHealthIndicator indicator = createIndicator("   ", "Test Gateway");

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("When health URL is configured")
    class WithHealthUrl {

        @Test
        @DisplayName("should return DOWN when URL is unreachable")
        void shouldReturnDownWhenUnreachable() {
            PaymentGatewayHealthIndicator indicator = createIndicator(
                    "http://192.0.2.1:1/health", "Test Gateway");

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("service", "Test Gateway");
            assertThat(health.getDetails()).containsKey("error");
        }

        @Test
        @DisplayName("should return DOWN when URL is invalid")
        void shouldReturnDownWhenUrlIsInvalid() {
            PaymentGatewayHealthIndicator indicator = createIndicator(
                    "not-a-valid-url://bad", "Test Gateway");

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("service", "Test Gateway");
        }
    }

    private PaymentGatewayHealthIndicator createIndicator(String healthUrl, String gatewayName) {
        PaymentGatewayHealthIndicator indicator = new PaymentGatewayHealthIndicator();
        ReflectionTestUtils.setField(indicator, "healthUrl", healthUrl);
        ReflectionTestUtils.setField(indicator, "gatewayName", gatewayName);
        return indicator;
    }
}
