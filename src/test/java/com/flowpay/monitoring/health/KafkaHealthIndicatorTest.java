package com.flowpay.monitoring.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaHealthIndicator Tests")
class KafkaHealthIndicatorTest {

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Mock
    private AdminClient adminClient;

    @Mock
    private DescribeClusterResult describeClusterResult;

    private KafkaHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new KafkaHealthIndicator(kafkaAdmin);
    }

    @Nested
    @DisplayName("When Kafka is healthy")
    class HealthyKafka {

        @Test
        @DisplayName("should return UP with cluster details")
        void shouldReturnUpWithClusterDetails() throws Exception {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", "localhost:9092");
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);

            KafkaFuture<String> clusterIdFuture = KafkaFuture.completedFuture("test-cluster-id");
            Node controllerNode = new Node(0, "localhost", 9092);
            KafkaFuture<Node> controllerFuture = KafkaFuture.completedFuture(controllerNode);
            Collection<Node> nodes = List.of(
                    new Node(0, "localhost", 9092),
                    new Node(1, "localhost", 9093)
            );
            KafkaFuture<Collection<Node>> nodesFuture = KafkaFuture.completedFuture(nodes);

            when(describeClusterResult.clusterId()).thenReturn(clusterIdFuture);
            when(describeClusterResult.controller()).thenReturn(controllerFuture);
            when(describeClusterResult.nodes()).thenReturn(nodesFuture);

            try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
                adminClientMock.when(() -> AdminClient.create(anyMap())).thenReturn(adminClient);
                when(adminClient.describeCluster()).thenReturn(describeClusterResult);

                Health health = healthIndicator.health();

                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails()).containsEntry("service", "Kafka");
                assertThat(health.getDetails()).containsEntry("clusterId", "test-cluster-id");
                assertThat(health.getDetails()).containsEntry("brokerCount", 2);
                assertThat(health.getDetails()).containsEntry("controllerId", 0);
            }
        }
    }

    @Nested
    @DisplayName("When Kafka is unhealthy")
    class UnhealthyKafka {

        @Test
        @DisplayName("should return DOWN when AdminClient creation fails")
        void shouldReturnDownWhenAdminClientFails() {
            Map<String, Object> config = new HashMap<>();
            config.put("bootstrap.servers", "localhost:9092");
            when(kafkaAdmin.getConfigurationProperties()).thenReturn(config);

            try (MockedStatic<AdminClient> adminClientMock = mockStatic(AdminClient.class)) {
                adminClientMock.when(() -> AdminClient.create(anyMap()))
                        .thenThrow(new RuntimeException("Connection refused"));

                Health health = healthIndicator.health();

                assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                assertThat(health.getDetails()).containsEntry("service", "Kafka");
                assertThat(health.getDetails()).containsKey("error");
            }
        }
    }
}
