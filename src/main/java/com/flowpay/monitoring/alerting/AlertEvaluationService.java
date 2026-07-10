package com.flowpay.monitoring.alerting;

import com.flowpay.monitoring.alerting.AlertEvent.AlertType;
import com.flowpay.monitoring.alerting.AlertEvent.Severity;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final MeterRegistry meterRegistry;
    private final List<AlertNotificationService> notificationServices;
    private final DataSource dataSource;

    @Value("${flowpay.alerting.thresholds.error-rate:0.05}")
    private double errorRateThreshold;

    @Value("${flowpay.alerting.thresholds.latency-p99-seconds:2.0}")
    private double latencyP99Threshold;

    @Value("${flowpay.alerting.thresholds.db-pool-utilization:0.85}")
    private double dbPoolUtilizationThreshold;

    @Value("${flowpay.alerting.enabled:true}")
    private boolean alertingEnabled;

    private final ConcurrentHashMap<AlertType, Boolean> activeAlerts = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${flowpay.alerting.evaluation-interval-ms:30000}")
    public void evaluateAlerts() {
        if (!alertingEnabled) {
            return;
        }

        evaluateErrorRate();
        evaluateLatency();
        evaluateDbPoolUtilization();
    }

    private void evaluateErrorRate() {
        try {
            double totalCount = getCounterValue("payment_transactions_total");
            double failedCount = getCounterValue("payment_transactions_total", "status", "FAILED");

            if (totalCount == 0) {
                return;
            }

            double errorRate = failedCount / totalCount;

            if (errorRate > errorRateThreshold) {
                fireAlert(AlertEvent.builder()
                        .alertType(AlertType.HIGH_ERROR_RATE)
                        .severity(Severity.CRITICAL)
                        .title("High Payment Error Rate")
                        .description(String.format("Payment failure rate is %.2f%% (threshold: %.2f%%)",
                                errorRate * 100, errorRateThreshold * 100))
                        .service("flowpay-core")
                        .currentValue(errorRate)
                        .threshold(errorRateThreshold)
                        .firedAt(Instant.now())
                        .labels(Map.of("metric", "error_rate"))
                        .build());
            } else {
                resolveAlert(AlertType.HIGH_ERROR_RATE, "High Payment Error Rate", errorRate);
            }
        } catch (Exception e) {
            log.debug("Error evaluating error rate alert: {}", e.getMessage());
        }
    }

    private void evaluateLatency() {
        try {
            Timer timer = meterRegistry.find("payment_processing_duration_seconds").timer();
            if (timer == null || timer.count() == 0) {
                return;
            }

            var snapshot = timer.takeSnapshot();
            double p99 = 0.0;
            for (var percentile : snapshot.percentileValues()) {
                if (Double.compare(percentile.percentile(), 0.99) == 0) {
                    p99 = percentile.value(TimeUnit.SECONDS);
                    break;
                }
            }

            if (p99 <= 0.0) {
                return;
            }

            if (p99 > latencyP99Threshold) {
                fireAlert(AlertEvent.builder()
                        .alertType(AlertType.HIGH_LATENCY)
                        .severity(Severity.CRITICAL)
                        .title("Transaction Processing Latency P99 > 2s")
                        .description(String.format("P99 latency is %.3fs (threshold: %.1fs)", p99, latencyP99Threshold))
                        .service("flowpay-core")
                        .currentValue(p99)
                        .threshold(latencyP99Threshold)
                        .firedAt(Instant.now())
                        .labels(Map.of("metric", "latency_p99"))
                        .build());
            } else {
                resolveAlert(AlertType.HIGH_LATENCY, "Transaction Processing Latency P99 > 2s", p99);
            }
        } catch (Exception e) {
            log.debug("Error evaluating latency alert: {}", e.getMessage());
        }
    }

    private void evaluateDbPoolUtilization() {
        try {
            if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
                return;
            }

            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
            if (poolMXBean == null) {
                return;
            }

            int activeConnections = poolMXBean.getActiveConnections();
            int totalConnections = poolMXBean.getTotalConnections();

            if (totalConnections == 0) {
                return;
            }

            double utilization = (double) activeConnections / totalConnections;

            if (utilization > dbPoolUtilizationThreshold) {
                fireAlert(AlertEvent.builder()
                        .alertType(AlertType.DB_POOL_EXHAUSTION)
                        .severity(Severity.CRITICAL)
                        .title("Database Connection Pool Near Exhaustion")
                        .description(String.format("Pool utilization is %.1f%% (%d/%d active connections, threshold: %.0f%%)",
                                utilization * 100, activeConnections, totalConnections, dbPoolUtilizationThreshold * 100))
                        .service("flowpay-core")
                        .currentValue(utilization)
                        .threshold(dbPoolUtilizationThreshold)
                        .firedAt(Instant.now())
                        .labels(Map.of("active", String.valueOf(activeConnections), "total", String.valueOf(totalConnections)))
                        .build());
            } else {
                resolveAlert(AlertType.DB_POOL_EXHAUSTION, "Database Connection Pool Near Exhaustion", utilization);
            }
        } catch (Exception e) {
            log.debug("Error evaluating DB pool alert: {}", e.getMessage());
        }
    }

    private void fireAlert(AlertEvent event) {
        Boolean alreadyFired = activeAlerts.putIfAbsent(event.getAlertType(), Boolean.TRUE);
        if (alreadyFired != null) {
            return; // Already active, don't spam
        }

        log.warn("ALERT FIRED: [{}] {} - {}", event.getSeverity(), event.getTitle(), event.getDescription());
        notificationServices.stream()
                .filter(AlertNotificationService::isEnabled)
                .forEach(service -> service.sendAlert(event));
    }

    private void resolveAlert(AlertType alertType, String title, double currentValue) {
        Boolean wasActive = activeAlerts.remove(alertType);
        if (wasActive == null) {
            return; // Wasn't active
        }

        AlertEvent recoveryEvent = AlertEvent.builder()
                .alertType(alertType)
                .severity(Severity.INFO)
                .title(title)
                .description("Alert resolved")
                .service("flowpay-core")
                .currentValue(currentValue)
                .threshold(0)
                .firedAt(Instant.now())
                .build();

        log.info("ALERT RESOLVED: {}", title);
        notificationServices.stream()
                .filter(AlertNotificationService::isEnabled)
                .forEach(service -> service.sendRecovery(recoveryEvent));
    }

    private double getCounterValue(String metricName) {
        var counter = meterRegistry.find(metricName).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double getCounterValue(String metricName, String tagKey, String tagValue) {
        var counter = meterRegistry.find(metricName).tag(tagKey, tagValue).counter();
        return counter != null ? counter.count() : 0.0;
    }
}
