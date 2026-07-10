package com.flowpay.monitoring.alerting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "flowpay.alerting.pagerduty.enabled", havingValue = "true")
public class PagerDutyAlertNotificationService implements AlertNotificationService {

    private static final String PAGERDUTY_EVENTS_URL = "https://events.pagerduty.com/v2/enqueue";

    private final RestTemplate restTemplate;
    private final String routingKey;
    private final String environment;

    public PagerDutyAlertNotificationService(
            @Value("${flowpay.alerting.pagerduty.routing-key}") String routingKey,
            @Value("${flowpay.alerting.environment:production}") String environment) {
        this.restTemplate = new RestTemplate();
        this.routingKey = routingKey;
        this.environment = environment;
    }

    @Override
    public void sendAlert(AlertEvent alertEvent) {
        try {
            Map<String, Object> payload = buildTriggerPayload(alertEvent);
            sendToPagerDuty(payload);
            log.info("PagerDuty alert triggered: [{}] {}", alertEvent.getSeverity(), alertEvent.getTitle());
        } catch (Exception e) {
            log.error("Failed to send PagerDuty alert: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendRecovery(AlertEvent alertEvent) {
        try {
            Map<String, Object> payload = buildResolvePayload(alertEvent);
            sendToPagerDuty(payload);
            log.info("PagerDuty incident resolved: {}", alertEvent.getTitle());
        } catch (Exception e) {
            log.error("Failed to send PagerDuty resolution: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        return routingKey != null && !routingKey.isBlank();
    }

    private Map<String, Object> buildTriggerPayload(AlertEvent event) {
        String timestamp = event.getFiredAt().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> payloadBody = new HashMap<>();
        payloadBody.put("summary", String.format("[%s][%s] %s", environment.toUpperCase(), event.getSeverity(), event.getTitle()));
        payloadBody.put("timestamp", timestamp);
        payloadBody.put("severity", mapSeverity(event.getSeverity()));
        payloadBody.put("source", event.getService());
        payloadBody.put("component", "flowpay-core");
        payloadBody.put("group", event.getAlertType().name());
        payloadBody.put("class", "payment_system");
        payloadBody.put("custom_details", Map.of(
                "description", event.getDescription(),
                "current_value", String.format("%.4f", event.getCurrentValue()),
                "threshold", String.format("%.4f", event.getThreshold()),
                "environment", environment,
                "alert_type", event.getAlertType().name()
        ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("routing_key", routingKey);
        payload.put("event_action", "trigger");
        payload.put("dedup_key", generateDedupKey(event));
        payload.put("payload", payloadBody);

        return payload;
    }

    private Map<String, Object> buildResolvePayload(AlertEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("routing_key", routingKey);
        payload.put("event_action", "resolve");
        payload.put("dedup_key", generateDedupKey(event));

        return payload;
    }

    private void sendToPagerDuty(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(PAGERDUTY_EVENTS_URL, request, String.class);
    }

    private String mapSeverity(AlertEvent.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "critical";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }

    private String generateDedupKey(AlertEvent event) {
        return String.format("flowpay-%s-%s-%s", environment, event.getService(), event.getAlertType().name());
    }
}
