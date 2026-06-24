package com.flowpay.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "flowpay.rate-limiter")
public class RateLimiterProperties {

    private boolean enabled = true;
    private int defaultLimit = 100;
    private int defaultWindowSeconds = 60;
}
