package com.flowpay.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookSignatureUtil")
class WebhookSignatureUtilTest {

    @Nested
    @DisplayName("generateSignature")
    class GenerateSignature {

        @Test
        @DisplayName("should generate consistent HMAC-SHA256 signature")
        void shouldGenerateConsistentSignature() {
            String payload = "{\"event\":\"PAYMENT_COMPLETED\",\"transactionId\":\"123\"}";
            String secret = "my-secret-key";

            String signature1 = WebhookSignatureUtil.generateSignature(payload, secret);
            String signature2 = WebhookSignatureUtil.generateSignature(payload, secret);

            assertThat(signature1).isEqualTo(signature2);
            assertThat(signature1).startsWith("sha256=");
        }

        @Test
        @DisplayName("should generate different signatures for different payloads")
        void shouldGenerateDifferentSignaturesForDifferentPayloads() {
            String secret = "my-secret-key";
            String payload1 = "{\"event\":\"PAYMENT_COMPLETED\"}";
            String payload2 = "{\"event\":\"PAYMENT_FAILED\"}";

            String signature1 = WebhookSignatureUtil.generateSignature(payload1, secret);
            String signature2 = WebhookSignatureUtil.generateSignature(payload2, secret);

            assertThat(signature1).isNotEqualTo(signature2);
        }

        @Test
        @DisplayName("should generate different signatures for different secrets")
        void shouldGenerateDifferentSignaturesForDifferentSecrets() {
            String payload = "{\"event\":\"PAYMENT_COMPLETED\"}";
            String secret1 = "secret-1";
            String secret2 = "secret-2";

            String signature1 = WebhookSignatureUtil.generateSignature(payload, secret1);
            String signature2 = WebhookSignatureUtil.generateSignature(payload, secret2);

            assertThat(signature1).isNotEqualTo(signature2);
        }
    }

    @Nested
    @DisplayName("verifySignature")
    class VerifySignature {

        @Test
        @DisplayName("should return true for valid signature")
        void shouldReturnTrueForValidSignature() {
            String payload = "{\"event\":\"PAYMENT_COMPLETED\",\"transactionId\":\"123\"}";
            String secret = "my-secret-key";

            String signature = WebhookSignatureUtil.generateSignature(payload, secret);

            assertThat(WebhookSignatureUtil.verifySignature(payload, secret, signature)).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid signature")
        void shouldReturnFalseForInvalidSignature() {
            String payload = "{\"event\":\"PAYMENT_COMPLETED\"}";
            String secret = "my-secret-key";

            assertThat(WebhookSignatureUtil.verifySignature(payload, secret, "sha256=invalid"))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for tampered payload")
        void shouldReturnFalseForTamperedPayload() {
            String originalPayload = "{\"event\":\"PAYMENT_COMPLETED\",\"amount\":100}";
            String tamperedPayload = "{\"event\":\"PAYMENT_COMPLETED\",\"amount\":999}";
            String secret = "my-secret-key";

            String signature = WebhookSignatureUtil.generateSignature(originalPayload, secret);

            assertThat(WebhookSignatureUtil.verifySignature(tamperedPayload, secret, signature))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for wrong secret")
        void shouldReturnFalseForWrongSecret() {
            String payload = "{\"event\":\"PAYMENT_COMPLETED\"}";
            String correctSecret = "correct-secret";
            String wrongSecret = "wrong-secret";

            String signature = WebhookSignatureUtil.generateSignature(payload, correctSecret);

            assertThat(WebhookSignatureUtil.verifySignature(payload, wrongSecret, signature))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for null signature")
        void shouldReturnFalseForNullSignature() {
            String payload = "{\"event\":\"PAYMENT_COMPLETED\"}";
            String secret = "my-secret-key";

            assertThat(WebhookSignatureUtil.verifySignature(payload, secret, null))
                    .isFalse();
        }
    }
}
