package com.flowpay.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskingConverterTest {

    private SensitiveDataMaskingConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SensitiveDataMaskingConverter();
    }

    private LoggingEvent createEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setMessage(message);
        return event;
    }

    @Nested
    @DisplayName("Password Masking")
    class PasswordMasking {

        @Test
        @DisplayName("Should mask password field in JSON")
        void shouldMaskPasswordField() {
            String input = "{\"username\":\"john\",\"password\":\"secret123\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).contains("\"password\":\"****\"");
            assertThat(result).doesNotContain("secret123");
        }

        @Test
        @DisplayName("Should mask password case insensitively")
        void shouldMaskPasswordCaseInsensitive() {
            String input = "{\"Password\":\"mypass\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("mypass");
        }
    }

    @Nested
    @DisplayName("Token Masking")
    class TokenMasking {

        @Test
        @DisplayName("Should mask access_token field")
        void shouldMaskAccessToken() {
            String input = "{\"access_token\":\"eyJhbGciOiJIUzI1NiJ9.payload.sig\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("Should mask Bearer token in log messages")
        void shouldMaskBearerToken() {
            String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
            assertThat(result).contains("Bearer ****");
        }
    }

    @Nested
    @DisplayName("Card Number Masking")
    class CardNumberMasking {

        @Test
        @DisplayName("Should mask card_number field in JSON")
        void shouldMaskCardNumberField() {
            String input = "{\"card_number\":\"4111111111111111\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("4111111111111111");
            assertThat(result).contains("****");
        }

        @Test
        @DisplayName("Should mask inline card numbers with spaces")
        void shouldMaskInlineCardWithSpaces() {
            String input = "Card: 4111 1111 1111 1111 was charged";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("4111 1111 1111 1111");
            assertThat(result).contains("4111-****-****-1111");
        }

        @Test
        @DisplayName("Should mask inline card numbers with dashes")
        void shouldMaskInlineCardWithDashes() {
            String input = "Card: 4111-1111-1111-1111 was charged";
            String result = converter.convert(createEvent(input));
            assertThat(result).contains("4111-****-****-1111");
        }
    }

    @Nested
    @DisplayName("CVV Masking")
    class CvvMasking {

        @Test
        @DisplayName("Should mask CVV field")
        void shouldMaskCvvField() {
            String input = "{\"cvv\":\"123\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("123");
            assertThat(result).contains("***");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null message")
        void shouldHandleNullMessage() {
            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.INFO);
            event.setMessage(null);
            String result = converter.convert(event);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should not mask non-sensitive data")
        void shouldNotMaskNonSensitiveData() {
            String input = "{\"username\":\"john\",\"email\":\"john@example.com\",\"amount\":\"100.00\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("Should mask SSN field")
        void shouldMaskSsnField() {
            String input = "{\"ssn\":\"123-45-6789\"}";
            String result = converter.convert(createEvent(input));
            assertThat(result).doesNotContain("123-45-6789");
            assertThat(result).contains("****");
        }
    }
}
