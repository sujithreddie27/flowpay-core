package com.flowpay.monitoring.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 5;
    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();

            String clusterId = clusterResult.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Collection<Node> nodes = clusterResult.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Node controller = clusterResult.controller().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return Health.up()
                    .withDetail("service", "Kafka")
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", nodes.size())
                    .withDetail("controllerId", controller != null ? controller.id() : "unknown")
                    .withDetail("bootstrapServers", kafkaAdmin.getConfigurationProperties()
                            .get("bootstrap.servers"))
                    .build();
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            return Health.down()
                    .withDetail("service", "Kafka")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
