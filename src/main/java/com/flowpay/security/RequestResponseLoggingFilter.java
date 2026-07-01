package com.flowpay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "x-api-key"
    );

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(\"password\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(\"(?:access_?token|refresh_?token|token)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
            "(\"(?:card_?number|cardNumber|pan)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(\"(?:cvv|cvc|security_?code)\"\\s*:\\s*\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE);

    private static final int MAX_BODY_LOG_LENGTH = 2048;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (isActuatorRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        long startTime = System.currentTimeMillis();

        MDC.put("requestId", requestId);
        try {
            wrappedResponse.setHeader("X-Request-ID", requestId);
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequest(wrappedRequest, requestId);
            logResponse(wrappedResponse, requestId, duration);
            wrappedResponse.copyBodyToResponse();
            MDC.remove("requestId");
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, String requestId) {
        String body = getRequestBody(request);
        String maskedBody = maskSensitiveData(body);

        log.info("REQUEST [{}] {} {} | IP={} | User-Agent={} | Body={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                getClientIp(request),
                maskHeader(request.getHeader("User-Agent")),
                maskedBody.isEmpty() ? "<empty>" : truncate(maskedBody));
    }

    private void logResponse(ContentCachingResponseWrapper response, String requestId, long duration) {
        int status = response.getStatus();
        if (status >= 400) {
            String body = getResponseBody(response);
            String maskedBody = maskSensitiveData(body);
            log.warn("RESPONSE [{}] Status={} | Duration={}ms | Body={}",
                    requestId, status, duration, truncate(maskedBody));
        } else {
            log.info("RESPONSE [{}] Status={} | Duration={}ms",
                    requestId, status, duration);
        }
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private String maskSensitiveData(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String masked = PASSWORD_PATTERN.matcher(body).replaceAll("$1****$3");
        masked = TOKEN_PATTERN.matcher(masked).replaceAll("$1****$3");
        masked = CARD_NUMBER_PATTERN.matcher(masked).replaceAll("$1****$3");
        masked = CVV_PATTERN.matcher(masked).replaceAll("$1***$3");
        return masked;
    }

    private String maskHeader(String headerValue) {
        if (headerValue == null) {
            return "<none>";
        }
        if (headerValue.length() > 100) {
            return headerValue.substring(0, 100) + "...";
        }
        return headerValue;
    }

    private String truncate(String body) {
        if (body.length() > MAX_BODY_LOG_LENGTH) {
            return body.substring(0, MAX_BODY_LOG_LENGTH) + "...[truncated]";
        }
        return body;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isActuatorRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
