package com.flowpay.transaction.service;

import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface TransactionService {

    TransactionResponse initiatePayment(InitiateTransactionRequest request);

    TransactionResponse getTransactionById(UUID transactionId);

    TransactionResponse getTransactionByReferenceId(String referenceId);

    Page<TransactionResponse> getTransactionsByUserId(UUID userId, TransactionFilterRequest filter);

    Page<TransactionResponse> getTransactionsBySenderId(UUID senderId, TransactionFilterRequest filter);

    Page<TransactionResponse> getTransactionsByReceiverId(UUID receiverId, TransactionFilterRequest filter);

    TransactionResponse cancelTransaction(UUID transactionId);
}
