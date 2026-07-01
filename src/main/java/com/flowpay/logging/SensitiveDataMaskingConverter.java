package com.flowpay.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

public class SensitiveDataMaskingConverter extends ClassicConverter {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(\"(?:password|passwd|secret|credentials)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(\"(?:access_?token|refresh_?token|token|api_?key|apiKey)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
            "(\"(?:card_?number|cardNumber|pan|account_?number)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(\"(?:cvv|cvc|security_?code)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "(\"(?:ssn|social_?security)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(Bearer\\s+)([A-Za-z0-9\\-._~+/]+=*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_INLINE_PATTERN = Pattern.compile(
            "\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return "";
        }
        return maskSensitiveData(message);
    }

    private String maskSensitiveData(String message) {
        String masked = PASSWORD_PATTERN.matcher(message).replaceAll("$1****$3");
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1****$3");
        masked = CARD_NUMBER_PATTERN.matcher(masked).replaceAll("$1****$3");
        masked = CVV_PATTERN.matcher(masked).replaceAll("$1***$3");
        masked = SSN_PATTERN.matcher(masked).replaceAll("$1****$3");
        masked = BEARER_TOKEN_PATTERN.matcher(masked).replaceAll("$1****");
        masked = CARD_INLINE_PATTERN.matcher(masked).replaceAll("$1-****-****-$4");
        return masked;
    }
}
