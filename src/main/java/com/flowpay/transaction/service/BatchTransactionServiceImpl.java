package com.flowpay.transaction.service;

import com.flowpay.transaction.dto.*;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class BatchTransactionServiceImpl implements BatchTransactionService {

    private final TransactionService transactionService;
    private final Counter batchSuccessCounter;
    private final Counter batchFailureCounter;

    public BatchTransactionServiceImpl(TransactionService transactionService, MeterRegistry meterRegistry) {
        this.transactionService = transactionService;
        this.batchSuccessCounter = Counter.builder("payment.batch.success.total")
                .description("Total successful batch transactions")
                .register(meterRegistry);
        this.batchFailureCounter = Counter.builder("payment.batch.failure.total")
                .description("Total failed batch transactions")
                .register(meterRegistry);
    }

    @Override
    @Timed(value = "payment.batch.duration", description = "Time taken to process a batch of payments")
    public BatchTransactionResponse processBatch(BatchTransactionRequest request) {
        String batchId = UUID.randomUUID().toString();
        OffsetDateTime submittedAt = OffsetDateTime.now();

        log.info("Processing batch: batchId={}, size={}", batchId, request.getTransactions().size());

        List<BatchTransactionResponse.BatchItemResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < request.getTransactions().size(); i++) {
            InitiateTransactionRequest txRequest = request.getTransactions().get(i);
            try {
                TransactionResponse response = transactionService.initiatePayment(txRequest);
                results.add(BatchTransactionResponse.BatchItemResult.builder()
                        .index(i)
                        .transaction(response)
                        .success(true)
                        .build());
                successCount++;
                batchSuccessCounter.increment();
            } catch (Exception e) {
                log.warn("Batch item {} failed: {}", i, e.getMessage());
                results.add(BatchTransactionResponse.BatchItemResult.builder()
                        .index(i)
                        .error(e.getMessage())
                        .success(false)
                        .build());
                failedCount++;
                batchFailureCounter.increment();
            }
        }

        log.info("Batch completed: batchId={}, success={}, failed={}",
                batchId, successCount, failedCount);

        return BatchTransactionResponse.builder()
                .batchId(batchId)
                .totalRequested(request.getTransactions().size())
                .successCount(successCount)
                .failedCount(failedCount)
                .results(results)
                .submittedAt(submittedAt)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    @Async("batchProcessingExecutor")
    @Timed(value = "payment.batch.async.duration", description = "Time taken to process async batch")
    public CompletableFuture<BatchTransactionResponse> processBatchAsync(BatchTransactionRequest request) {
        log.info("Processing async batch: size={}", request.getTransactions().size());
        BatchTransactionResponse response = processBatch(request);
        return CompletableFuture.completedFuture(response);
    }
}
