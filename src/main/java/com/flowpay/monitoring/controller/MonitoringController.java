package com.flowpay.monitoring.controller;

import com.flowpay.common.dto.ApiResponse;
import com.flowpay.monitoring.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Monitoring", description = "System monitoring and observability APIs")
public class MonitoringController {

    private final MeterRegistry meterRegistry;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/health")
    @Operation(summary = "Aggregate health from actuator")
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        HealthComponent health = healthEndpoint.health();
        Map<String, HealthResponse.ComponentHealth> components = new LinkedHashMap<>();

        if (health instanceof CompositeHealth compositeHealth) {
            compositeHealth.getComponents().forEach((name, component) -> {
                components.put(name, HealthResponse.ComponentHealth.builder()
                        .status(component.getStatus().getCode())
                        .details(Map.of())
                        .build());
            });
        }

        HealthResponse response = HealthResponse.builder()
                .status(health.getStatus().getCode())
                .components(components)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/response-times")
    @Operation(summary = "Micrometer timer percentiles")
    public ResponseEntity<ApiResponse<ResponseTimesResponse>> getResponseTimes() {
        Timer timer = meterRegistry.find("http.server.requests").timer();

        if (timer == null || timer.count() == 0) {
            return ResponseEntity.ok(ApiResponse.success(ResponseTimesResponse.builder()
                    .p50Ms(0).p95Ms(0).p99Ms(0).meanMs(0).maxMs(0).totalRequests(0)
                    .build()));
        }

        var snapshot = timer.takeSnapshot();
        double p50 = 0, p95 = 0, p99 = 0;
        for (var pv : snapshot.percentileValues()) {
            if (Double.compare(pv.percentile(), 0.5) == 0) p50 = pv.value(TimeUnit.MILLISECONDS);
            if (Double.compare(pv.percentile(), 0.95) == 0) p95 = pv.value(TimeUnit.MILLISECONDS);
            if (Double.compare(pv.percentile(), 0.99) == 0) p99 = pv.value(TimeUnit.MILLISECONDS);
        }

        ResponseTimesResponse response = ResponseTimesResponse.builder()
                .p50Ms(p50)
                .p95Ms(p95)
                .p99Ms(p99)
                .meanMs(timer.mean(TimeUnit.MILLISECONDS))
                .maxMs(timer.max(TimeUnit.MILLISECONDS))
                .totalRequests(timer.count())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/error-rates")
    @Operation(summary = "Error counter data")
    public ResponseEntity<ApiResponse<ErrorRatesResponse>> getErrorRates() {
        double totalCount = getCounterValue("payment_transactions_total");
        double failedCount = getCounterValue("payment_transactions_total", "status", "FAILED");

        Map<String, Long> errorsByType = new LinkedHashMap<>();
        Collection<Counter> counters = meterRegistry.find("payment_transactions_total")
                .tag("status", "FAILED")
                .counters();
        for (Counter counter : counters) {
            String type = counter.getId().getTag("type");
            if (type != null) {
                errorsByType.put(type, (long) counter.count());
            }
        }

        double errorRate = totalCount > 0 ? failedCount / totalCount : 0;

        ErrorRatesResponse response = ErrorRatesResponse.builder()
                .overallErrorRate(errorRate)
                .totalRequests((long) totalCount)
                .totalErrors((long) failedCount)
                .errorsByType(errorsByType)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/kafka-lag")
    @Operation(summary = "Kafka consumer group lag")
    public ResponseEntity<ApiResponse<KafkaLagResponse>> getKafkaLag() {
        Map<String, KafkaLagResponse.ConsumerGroupLag> consumerGroups = new LinkedHashMap<>();

        Collection<Gauge> lagGauges = meterRegistry.find("kafka.consumer.fetch.manager.records.lag.max")
                .gauges();

        Map<String, Map<String, Long>> groupedLag = new LinkedHashMap<>();
        for (Gauge gauge : lagGauges) {
            String clientId = gauge.getId().getTag("client.id");
            String topic = gauge.getId().getTag("topic");
            String partition = gauge.getId().getTag("partition");
            String key = clientId != null ? clientId : "default";
            String partKey = (topic != null ? topic : "unknown") + "-" + (partition != null ? partition : "0");

            groupedLag.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .put(partKey, (long) gauge.value());
        }

        groupedLag.forEach((group, partitions) -> {
            long totalLag = partitions.values().stream().mapToLong(Long::longValue).sum();
            consumerGroups.put(group, KafkaLagResponse.ConsumerGroupLag.builder()
                    .totalLag(totalLag)
                    .partitionLag(partitions)
                    .build());
        });

        KafkaLagResponse response = KafkaLagResponse.builder()
                .consumerGroups(consumerGroups)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Active alert list")
    public ResponseEntity<ApiResponse<List<AlertResponse>>> getAlerts() {
        // Collect alerts from metrics thresholds
        List<AlertResponse> alerts = new ArrayList<>();

        // Check error rate
        double totalCount = getCounterValue("payment_transactions_total");
        double failedCount = getCounterValue("payment_transactions_total", "status", "FAILED");
        if (totalCount > 0 && (failedCount / totalCount) > 0.05) {
            alerts.add(AlertResponse.builder()
                    .alertType("HIGH_ERROR_RATE")
                    .severity("CRITICAL")
                    .title("High Payment Error Rate")
                    .description(String.format("Error rate: %.2f%%", (failedCount / totalCount) * 100))
                    .service("flowpay-core")
                    .currentValue(failedCount / totalCount)
                    .threshold(0.05)
                    .build());
        }

        // Check latency
        Timer timer = meterRegistry.find("payment.initiate.duration").timer();
        if (timer != null && timer.count() > 0) {
            double maxMs = timer.max(TimeUnit.MILLISECONDS);
            if (maxMs > 2000) {
                alerts.add(AlertResponse.builder()
                        .alertType("HIGH_LATENCY")
                        .severity("WARNING")
                        .title("High Payment Latency")
                        .description(String.format("Max latency: %.0fms", maxMs))
                        .service("flowpay-core")
                        .currentValue(maxMs)
                        .threshold(2000)
                        .build());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    private double getCounterValue(String name) {
        Collection<Counter> counters = meterRegistry.find(name).counters();
        return counters.stream().mapToDouble(Counter::count).sum();
    }

    private double getCounterValue(String name, String tagKey, String tagValue) {
        Collection<Counter> counters = meterRegistry.find(name).tag(tagKey, tagValue).counters();
        return counters.stream().mapToDouble(Counter::count).sum();
    }
}
