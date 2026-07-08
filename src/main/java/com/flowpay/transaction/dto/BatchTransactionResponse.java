package com.flowpay.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransactionResponse {

    private String batchId;
    private int totalRequested;
    private int successCount;
    private int failedCount;
    private List<BatchItemResult> results;
    private OffsetDateTime submittedAt;
    private OffsetDateTime completedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItemResult {
        private int index;
        private TransactionResponse transaction;
        private String error;
        private boolean success;
    }
}
