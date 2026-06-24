package com.flowpay.monitoring.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public Health health() {
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            String pong = connection.ping();
            connection.close();

            if ("PONG".equals(pong)) {
                return Health.up()
                        .withDetail("service", "Redis")
                        .withDetail("status", "Connected")
                        .build();
            }

            return Health.down()
                    .withDetail("service", "Redis")
                    .withDetail("status", "Unexpected ping response: " + pong)
                    .build();
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("service", "Redis")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
