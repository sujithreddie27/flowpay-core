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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "flowpay.alerting.slack.enabled", havingValue = "true")
public class SlackAlertNotificationService implements AlertNotificationService {

    private final RestTemplate restTemplate;
    private final String webhookUrl;
    private final String channel;
    private final String environment;

    public SlackAlertNotificationService(
            @Value("${flowpay.alerting.slack.webhook-url}") String webhookUrl,
            @Value("${flowpay.alerting.slack.channel:#alerts-payments}") String channel,
            @Value("${flowpay.alerting.environment:production}") String environment) {
        this.restTemplate = new RestTemplate();
        this.webhookUrl = webhookUrl;
        this.channel = channel;
        this.environment = environment;
    }

    @Override
    public void sendAlert(AlertEvent alertEvent) {
        try {
            Map<String, Object> payload = buildAlertPayload(alertEvent);
            sendToSlack(payload);
            log.info("Slack alert sent: [{}] {}", alertEvent.getSeverity(), alertEvent.getTitle());
        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendRecovery(AlertEvent alertEvent) {
        try {
            Map<String, Object> payload = buildRecoveryPayload(alertEvent);
            sendToSlack(payload);
            log.info("Slack recovery notification sent: {}", alertEvent.getTitle());
        } catch (Exception e) {
            log.error("Failed to send Slack recovery notification: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    private Map<String, Object> buildAlertPayload(AlertEvent event) {
        String color = getColorForSeverity(event.getSeverity());
        String emoji = getEmojiForSeverity(event.getSeverity());
        String timestamp = event.getFiredAt().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", color);
        attachment.put("title", emoji + " " + event.getTitle());
        attachment.put("text", event.getDescription());
        attachment.put("fields", List.of(
                Map.of("title", "Severity", "value", event.getSeverity().name(), "short", true),
                Map.of("title", "Service", "value", event.getService(), "short", true),
                Map.of("title", "Environment", "value", environment, "short", true),
                Map.of("title", "Alert Type", "value", event.getAlertType().name(), "short", true),
                Map.of("title", "Current Value", "value", String.format("%.4f", event.getCurrentValue()), "short", true),
                Map.of("title", "Threshold", "value", String.format("%.4f", event.getThreshold()), "short", true)
        ));
        attachment.put("footer", "FlowPay Alerting | " + timestamp);
        attachment.put("ts", event.getFiredAt().getEpochSecond());

        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", channel);
        payload.put("username", "FlowPay Alerts");
        payload.put("icon_emoji", ":rotating_light:");
        payload.put("attachments", List.of(attachment));

        return payload;
    }

    private Map<String, Object> buildRecoveryPayload(AlertEvent event) {
        String timestamp = event.getFiredAt().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", "#36a64f");
        attachment.put("title", ":white_check_mark: RESOLVED: " + event.getTitle());
        attachment.put("text", "Alert has been resolved. Current value: " + String.format("%.4f", event.getCurrentValue()));
        attachment.put("fields", List.of(
                Map.of("title", "Service", "value", event.getService(), "short", true),
                Map.of("title", "Environment", "value", environment, "short", true)
        ));
        attachment.put("footer", "FlowPay Alerting | " + timestamp);
        attachment.put("ts", event.getFiredAt().getEpochSecond());

        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", channel);
        payload.put("username", "FlowPay Alerts");
        payload.put("icon_emoji", ":white_check_mark:");
        payload.put("attachments", List.of(attachment));

        return payload;
    }

    private void sendToSlack(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(webhookUrl, request, String.class);
    }

    private String getColorForSeverity(AlertEvent.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#e01e5a";
            case WARNING -> "#ecb22e";
            case INFO -> "#2eb67d";
        };
    }

    private String getEmojiForSeverity(AlertEvent.Severity severity) {
        return switch (severity) {
            case CRITICAL -> ":red_circle:";
            case WARNING -> ":warning:";
            case INFO -> ":information_source:";
        };
    }
}
