package com.flowpay.cache;

import com.flowpay.common.ratelimit.RateLimiterService;
import com.flowpay.config.RateLimiterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limiter Service Tests")
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RateLimiterProperties rateLimiterProperties;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterProperties = new RateLimiterProperties();
        rateLimiterProperties.setEnabled(true);
        rateLimiterProperties.setDefaultLimit(10);
        rateLimiterProperties.setDefaultWindowSeconds(60);
        rateLimiterService = new RateLimiterService(stringRedisTemplate, rateLimiterProperties);
    }

    @Nested
    @DisplayName("isAllowed")
    class IsAllowed {

        @Test
        @DisplayName("Should allow request when under limit")
        void shouldAllowRequestUnderLimit() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                    .thenReturn(1L);

            boolean result = rateLimiterService.isAllowed("user:123");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should deny request when over limit")
        void shouldDenyRequestOverLimit() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                    .thenReturn(0L);

            boolean result = rateLimiterService.isAllowed("user:123");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should allow all requests when rate limiting is disabled")
        void shouldAllowAllWhenDisabled() {
            rateLimiterProperties.setEnabled(false);

            boolean result = rateLimiterService.isAllowed("user:123");

            assertThat(result).isTrue();
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("Should allow request with custom limit and window")
        void shouldAllowWithCustomLimitAndWindow() {
            when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                    .thenReturn(1L);

            boolean result = rateLimiterService.isAllowed("user:456", 50, 120);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getRemainingRequests")
    class GetRemainingRequests {

        @Test
        @DisplayName("Should return full limit when no requests made")
        void shouldReturnFullLimitWhenNoRequests() {
            when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
            when(zSetOperations.zCard(anyString())).thenReturn(0L);

            long remaining = rateLimiterService.getRemainingRequests("user:123");

            assertThat(remaining).isEqualTo(10);
        }

        @Test
        @DisplayName("Should return reduced count after some requests")
        void shouldReturnReducedCountAfterRequests() {
            when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
            when(zSetOperations.zCard(anyString())).thenReturn(7L);

            long remaining = rateLimiterService.getRemainingRequests("user:123");

            assertThat(remaining).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return zero when limit exhausted")
        void shouldReturnZeroWhenExhausted() {
            when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
            when(zSetOperations.zCard(anyString())).thenReturn(15L);

            long remaining = rateLimiterService.getRemainingRequests("user:123");

            assertThat(remaining).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return full limit when disabled")
        void shouldReturnFullLimitWhenDisabled() {
            rateLimiterProperties.setEnabled(false);

            long remaining = rateLimiterService.getRemainingRequests("user:123");

            assertThat(remaining).isEqualTo(10);
            verifyNoInteractions(stringRedisTemplate);
        }
    }
}
