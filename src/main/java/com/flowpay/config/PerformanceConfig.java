package com.flowpay.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.db.DatabaseTableMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class PerformanceConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("service", "flowpay-core");
    }

    @Bean
    public HikariPoolMetrics hikariPoolMetrics(DataSource dataSource, MeterRegistry meterRegistry) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.setMetricRegistry(meterRegistry);
            log.info("HikariCP metrics registered: pool={}", hikariDataSource.getPoolName());
        }
        return new HikariPoolMetrics();
    }

    public static class HikariPoolMetrics {
        // Marker class — HikariCP automatically registers metrics when MetricRegistry is set
    }
}
