package com.flowpay.common.ratelimit;

import com.flowpay.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flowpay.rate-limiter.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimiterProperties rateLimiterProperties;

    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local clearBefore = now - window
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
                redis.call('EXPIRE', key, window)
                return 1
            end
            return 0
            """;

    private final DefaultRedisScript<Long> slidingWindowScript = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);

    public boolean isAllowed(String key) {
        return isAllowed(key, rateLimiterProperties.getDefaultLimit(), rateLimiterProperties.getDefaultWindowSeconds());
    }

    public boolean isAllowed(String key, int limit, int windowSeconds) {
        if (!rateLimiterProperties.isEnabled()) {
            return true;
        }

        String redisKey = "flowpay:ratelimit:" + key;
        long now = System.currentTimeMillis();
        long windowMillis = (long) windowSeconds * 1000;

        List<String> keys = Collections.singletonList(redisKey);
        Long result = stringRedisTemplate.execute(slidingWindowScript, keys,
                String.valueOf(now), String.valueOf(windowMillis), String.valueOf(limit));

        boolean allowed = result != null && result == 1L;
        if (!allowed) {
            log.warn("Rate limit exceeded for key={}, limit={}, window={}s", key, limit, windowSeconds);
        }
        return allowed;
    }

    public long getRemainingRequests(String key) {
        return getRemainingRequests(key, rateLimiterProperties.getDefaultLimit(), rateLimiterProperties.getDefaultWindowSeconds());
    }

    public long getRemainingRequests(String key, int limit, int windowSeconds) {
        if (!rateLimiterProperties.isEnabled()) {
            return limit;
        }

        String redisKey = "flowpay:ratelimit:" + key;
        long now = System.currentTimeMillis();
        long windowMillis = (long) windowSeconds * 1000;
        long clearBefore = now - windowMillis;

        stringRedisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, clearBefore);
        Long count = stringRedisTemplate.opsForZSet().zCard(redisKey);
        long currentCount = count != null ? count : 0;

        return Math.max(0, limit - currentCount);
    }
}
