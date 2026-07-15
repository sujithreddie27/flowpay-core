package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionTimelineResponse {

    private UUID transactionId;
    private String referenceId;
    private List<TimelineEntry> timeline;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimelineEntry {
        private OffsetDateTime timestamp;
        private String action;
        private String description;
        private String performedBy;
    }
}
