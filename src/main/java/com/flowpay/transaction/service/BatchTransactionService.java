package com.flowpay.transaction.service;

import com.flowpay.transaction.dto.BatchTransactionRequest;
import com.flowpay.transaction.dto.BatchTransactionResponse;

import java.util.concurrent.CompletableFuture;

public interface BatchTransactionService {

    BatchTransactionResponse processBatch(BatchTransactionRequest request);

    CompletableFuture<BatchTransactionResponse> processBatchAsync(BatchTransactionRequest request);
}
