package com.flowpay.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaLagResponse {

    private Map<String, ConsumerGroupLag> consumerGroups;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumerGroupLag {
        private long totalLag;
        private Map<String, Long> partitionLag;
    }
}
