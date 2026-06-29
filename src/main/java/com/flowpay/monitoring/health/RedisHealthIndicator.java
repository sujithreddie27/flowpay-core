package com.flowpay.monitoring.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();

            if (!"PONG".equals(pong)) {
                return Health.down()
                        .withDetail("service", "Redis")
                        .withDetail("status", "Unexpected ping response: " + pong)
                        .build();
            }

            Health.Builder builder = Health.up()
                    .withDetail("service", "Redis");

            addServerInfo(connection, builder);

            return builder.build();
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("service", "Redis")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private void addServerInfo(RedisConnection connection, Health.Builder builder) {
        try {
            Properties serverInfo = connection.serverCommands().info("server");
            if (serverInfo != null) {
                builder.withDetail("version", serverInfo.getProperty("redis_version", "unknown"));
                builder.withDetail("mode", serverInfo.getProperty("redis_mode", "unknown"));
                builder.withDetail("uptimeSeconds", serverInfo.getProperty("uptime_in_seconds", "unknown"));
            }

            Properties memoryInfo = connection.serverCommands().info("memory");
            if (memoryInfo != null) {
                builder.withDetail("usedMemory", memoryInfo.getProperty("used_memory_human", "unknown"));
                builder.withDetail("maxMemory", memoryInfo.getProperty("maxmemory_human", "unknown"));
            }

            Properties clientInfo = connection.serverCommands().info("clients");
            if (clientInfo != null) {
                builder.withDetail("connectedClients", clientInfo.getProperty("connected_clients", "unknown"));
            }
        } catch (Exception e) {
            log.debug("Could not retrieve Redis server info", e);
        }
    }
}
