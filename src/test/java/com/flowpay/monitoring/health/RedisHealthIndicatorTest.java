package com.flowpay.monitoring.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisHealthIndicator Tests")
class RedisHealthIndicatorTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Mock
    private RedisServerCommands serverCommands;

    private RedisHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new RedisHealthIndicator(connectionFactory);
    }

    @Nested
    @DisplayName("When Redis is healthy")
    class HealthyRedis {

        @Test
        @DisplayName("should return UP with server details")
        void shouldReturnUpWithServerDetails() {
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.ping()).thenReturn("PONG");
            when(connection.serverCommands()).thenReturn(serverCommands);

            Properties serverInfo = new Properties();
            serverInfo.setProperty("redis_version", "7.2.4");
            serverInfo.setProperty("redis_mode", "standalone");
            serverInfo.setProperty("uptime_in_seconds", "86400");
            when(serverCommands.info("server")).thenReturn(serverInfo);

            Properties memoryInfo = new Properties();
            memoryInfo.setProperty("used_memory_human", "2.5M");
            memoryInfo.setProperty("maxmemory_human", "256M");
            when(serverCommands.info("memory")).thenReturn(memoryInfo);

            Properties clientInfo = new Properties();
            clientInfo.setProperty("connected_clients", "5");
            when(serverCommands.info("clients")).thenReturn(clientInfo);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("service", "Redis");
            assertThat(health.getDetails()).containsEntry("version", "7.2.4");
            assertThat(health.getDetails()).containsEntry("mode", "standalone");
            assertThat(health.getDetails()).containsEntry("uptimeSeconds", "86400");
            assertThat(health.getDetails()).containsEntry("usedMemory", "2.5M");
            assertThat(health.getDetails()).containsEntry("maxMemory", "256M");
            assertThat(health.getDetails()).containsEntry("connectedClients", "5");
        }

        @Test
        @DisplayName("should return UP even without server info")
        void shouldReturnUpWithoutServerInfo() {
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.ping()).thenReturn("PONG");
            when(connection.serverCommands()).thenReturn(serverCommands);
            when(serverCommands.info("server")).thenReturn(null);
            when(serverCommands.info("memory")).thenReturn(null);
            when(serverCommands.info("clients")).thenReturn(null);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("service", "Redis");
        }
    }

    @Nested
    @DisplayName("When Redis is unhealthy")
    class UnhealthyRedis {

        @Test
        @DisplayName("should return DOWN when ping returns unexpected response")
        void shouldReturnDownOnBadPing() {
            when(connectionFactory.getConnection()).thenReturn(connection);
            when(connection.ping()).thenReturn("ERROR");

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("service", "Redis");
            assertThat(health.getDetails().get("status").toString()).contains("Unexpected ping response");
        }

        @Test
        @DisplayName("should return DOWN when connection fails")
        void shouldReturnDownOnConnectionFailure() {
            when(connectionFactory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("service", "Redis");
            assertThat(health.getDetails()).containsEntry("error", "Connection refused");
        }
    }
}
