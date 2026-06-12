package com.flowpay.monitoring.info;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom info contributor for actuator /info endpoint.
 * Provides application metadata and runtime information.
 */
@Component
public class ApplicationInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> appDetails = new HashMap<>();
        appDetails.put("name", "FlowPay Core");
        appDetails.put("description", "Real-Time Payment Processing Platform");
        appDetails.put("version", "1.0.0-SNAPSHOT");
        
        Map<String, Object> techStack = new HashMap<>();
        techStack.put("language", "Java 17");
        techStack.put("framework", "Spring Boot 3.3.0");
        techStack.put("database", "PostgreSQL");
        techStack.put("cache", "Redis");
        techStack.put("messaging", "Apache Kafka");
        
        Map<String, Object> features = new HashMap<>();
        features.put("authentication", "JWT");
        features.put("database-migration", "Flyway");
        features.put("api-documentation", "OpenAPI 3.0");
        features.put("containerization", "Docker");
        
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("startup-time", LocalDateTime.now().toString());
        runtime.put("timezone", System.getProperty("user.timezone"));
        runtime.put("java-version", System.getProperty("java.version"));
        runtime.put("java-vendor", System.getProperty("java.vendor"));
        
        builder.withDetail("application", appDetails);
        builder.withDetail("tech-stack", techStack);
        builder.withDetail("features", features);
        builder.withDetail("runtime", runtime);
    }
}
