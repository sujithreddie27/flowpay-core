package com.flowpay.monitoring.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseHealthIndicator Tests")
class DatabaseHealthIndicatorTest {

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private HikariPoolMXBean poolMXBean;

    private DatabaseHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new DatabaseHealthIndicator(dataSource);
    }

    @Nested
    @DisplayName("When database is healthy")
    class HealthyDatabase {

        @Test
        @DisplayName("should return UP with database details")
        void shouldReturnUpWithDetails() throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(anyInt())).thenReturn(true);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getDatabaseProductVersion()).thenReturn("16.2");
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getLong(1)).thenReturn(52428800L); // 50MB
            when(dataSource.getHikariPoolMXBean()).thenReturn(poolMXBean);
            when(poolMXBean.getActiveConnections()).thenReturn(5);
            when(poolMXBean.getIdleConnections()).thenReturn(10);
            when(poolMXBean.getTotalConnections()).thenReturn(15);
            when(poolMXBean.getThreadsAwaitingConnection()).thenReturn(0);
            when(dataSource.getMaximumPoolSize()).thenReturn(20);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
            assertThat(health.getDetails()).containsEntry("version", "16.2");
            assertThat(health.getDetails()).containsKey("databaseSizeMB");
            assertThat(health.getDetails()).containsEntry("pool.activeConnections", 5);
            assertThat(health.getDetails()).containsEntry("pool.idleConnections", 10);
            assertThat(health.getDetails()).containsEntry("pool.totalConnections", 15);
            assertThat(health.getDetails()).containsEntry("pool.threadsAwaitingConnection", 0);
            assertThat(health.getDetails()).containsEntry("pool.maxPoolSize", 20);
        }

        @Test
        @DisplayName("should return UP even without pool MXBean")
        void shouldReturnUpWithoutPoolMXBean() throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(anyInt())).thenReturn(true);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getDatabaseProductVersion()).thenReturn("16.2");
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);
            when(dataSource.getHikariPoolMXBean()).thenReturn(null);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
        }
    }

    @Nested
    @DisplayName("When database is unhealthy")
    class UnhealthyDatabase {

        @Test
        @DisplayName("should return DOWN when connection validation fails")
        void shouldReturnDownOnValidationFailure() throws SQLException {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.isValid(anyInt())).thenReturn(false);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
            assertThat(health.getDetails()).containsEntry("status", "Connection validation failed");
        }

        @Test
        @DisplayName("should return DOWN when connection throws exception")
        void shouldReturnDownOnException() throws SQLException {
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("database", "PostgreSQL");
            assertThat(health.getDetails()).containsEntry("error", "Connection refused");
        }
    }
}
