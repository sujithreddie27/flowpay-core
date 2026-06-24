package com.flowpay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisConfig {

    public static final String CACHE_ACCOUNT_BALANCE = "accountBalance";
    public static final String CACHE_USER_PROFILE = "userProfile";
    public static final String CACHE_TRANSACTION_STATUS = "transactionStatus";
    public static final String CACHE_ACCOUNT = "account";

    @Value("${flowpay.cache.ttl.account-balance:300}")
    private long accountBalanceTtl;

    @Value("${flowpay.cache.ttl.user-profile:600}")
    private long userProfileTtl;

    @Value("${flowpay.cache.ttl.transaction-status:120}")
    private long transactionStatusTtl;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues()
                .prefixCacheNameWith("flowpay:");

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put(CACHE_ACCOUNT_BALANCE, defaultConfig
                .entryTtl(Duration.ofSeconds(accountBalanceTtl)));

        cacheConfigurations.put(CACHE_USER_PROFILE, defaultConfig
                .entryTtl(Duration.ofSeconds(userProfileTtl)));

        cacheConfigurations.put(CACHE_TRANSACTION_STATUS, defaultConfig
                .entryTtl(Duration.ofSeconds(transactionStatusTtl)));

        cacheConfigurations.put(CACHE_ACCOUNT, defaultConfig
                .entryTtl(Duration.ofSeconds(accountBalanceTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
