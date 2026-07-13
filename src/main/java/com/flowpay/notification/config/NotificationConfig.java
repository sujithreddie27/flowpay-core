package com.flowpay.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class NotificationConfig {

    @Value("${flowpay.notification.webhook.connect-timeout-ms:5000}")
    private int connectTimeout;

    @Value("${flowpay.notification.webhook.read-timeout-ms:10000}")
    private int readTimeout;

    @Bean
    public RestTemplate webhookRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
