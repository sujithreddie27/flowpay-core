package com.flowpay.monitoring.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("status", "Connection validation failed")
                        .build();
            }

            Health.Builder builder = Health.up()
                    .withDetail("database", "PostgreSQL");

            addDatabaseMetadata(connection, builder);
            addConnectionPoolDetails(builder);

            return builder.build();
        } catch (SQLException e) {
            log.error("Database health check failed", e);
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private void addDatabaseMetadata(Connection connection, Health.Builder builder) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            builder.withDetail("version", metaData.getDatabaseProductVersion());

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT pg_database_size(current_database())")) {
                if (rs.next()) {
                    long sizeBytes = rs.getLong(1);
                    builder.withDetail("databaseSizeMB", String.format("%.2f", sizeBytes / (1024.0 * 1024.0)));
                }
            }
        } catch (SQLException e) {
            log.debug("Could not retrieve database metadata", e);
        }
    }

    private void addConnectionPoolDetails(Health.Builder builder) {
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                builder.withDetail("pool.activeConnections", pool.getActiveConnections())
                        .withDetail("pool.idleConnections", pool.getIdleConnections())
                        .withDetail("pool.totalConnections", pool.getTotalConnections())
                        .withDetail("pool.threadsAwaitingConnection", pool.getThreadsAwaitingConnection())
                        .withDetail("pool.maxPoolSize", hikari.getMaximumPoolSize());
            }
        }
    }
}
