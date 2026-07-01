package com.flowpay.logging;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import net.logstash.logback.decorate.JsonGeneratorDecorator;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

public class SensitiveDataJsonMaskingDecorator implements JsonGeneratorDecorator {

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwd", "secret", "credentials",
            "access_token", "accessToken", "refresh_token", "refreshToken",
            "token", "api_key", "apiKey",
            "card_number", "cardNumber", "pan",
            "cvv", "cvc", "security_code", "securityCode",
            "ssn", "social_security", "socialSecurity",
            "password_hash", "passwordHash"
    );

    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(Bearer\\s+)([A-Za-z0-9\\-._~+/]+=*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_INLINE_PATTERN = Pattern.compile(
            "\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");

    @Override
    public JsonGenerator decorate(JsonGenerator generator) {
        return new MaskingJsonGenerator(generator);
    }

    private static class MaskingJsonGenerator extends com.fasterxml.jackson.core.util.JsonGeneratorDelegate {

        private String currentFieldName;

        MaskingJsonGenerator(JsonGenerator delegate) {
            super(delegate, true);
        }

        @Override
        public void writeFieldName(String name) throws IOException {
            this.currentFieldName = name;
            super.writeFieldName(name);
        }

        @Override
        public void writeFieldName(SerializableString name) throws IOException {
            this.currentFieldName = name.getValue();
            super.writeFieldName(name);
        }

        @Override
        public void writeString(String text) throws IOException {
            if (currentFieldName != null && SENSITIVE_FIELDS.contains(currentFieldName)) {
                super.writeString("****");
            } else if (text != null) {
                String masked = maskInlinePatterns(text);
                super.writeString(masked);
            } else {
                super.writeString(text);
            }
        }

        private String maskInlinePatterns(String text) {
            String masked = BEARER_PATTERN.matcher(text).replaceAll("$1****");
            masked = CARD_INLINE_PATTERN.matcher(masked).replaceAll("$1-****-****-$4");
            return masked;
        }
    }
}
