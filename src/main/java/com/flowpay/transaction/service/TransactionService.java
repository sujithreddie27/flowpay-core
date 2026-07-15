package com.flowpay.transaction.service;

import com.flowpay.transaction.dto.*;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionService {

    TransactionResponse initiatePayment(InitiateTransactionRequest request);

    TransactionResponse getTransactionById(UUID transactionId);

    TransactionResponse getTransactionByReferenceId(String referenceId);

    Page<TransactionResponse> getTransactionsByUserId(UUID userId, TransactionFilterRequest filter);

    Page<TransactionResponse> getTransactionsBySenderId(UUID senderId, TransactionFilterRequest filter);

    Page<TransactionResponse> getTransactionsByReceiverId(UUID receiverId, TransactionFilterRequest filter);

    Page<TransactionResponse> getTransactionHistory(TransactionFilterRequest filter);

    TransactionSummaryResponse getTransactionSummary(UUID userId, OffsetDateTime from, OffsetDateTime to);

    TransactionReceiptResponse getTransactionReceipt(UUID transactionId);

    TransactionResponse cancelTransaction(UUID transactionId);

    TransactionResponse retryTransaction(UUID transactionId);

    TransactionResponse reverseTransaction(UUID transactionId, String reason);

    List<TransactionResponse> getRetryableTransactions();

    int processStalePendingTransactions();

    TransactionStatusResponse getTransactionStatus(UUID transactionId);

    TransactionTimelineResponse getTransactionTimeline(UUID transactionId);

    byte[] exportTransactions(UUID userId, TransactionFilterRequest filter);
}
