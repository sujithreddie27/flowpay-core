package com.flowpay.payments.service;

import com.flowpay.common.dto.PagedResponse;
import com.flowpay.payments.dto.PaymentFilterRequest;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionResponse;

import java.util.UUID;

public interface PaymentService {

    PagedResponse<TransactionResponse> listPayments(UUID userId, PaymentFilterRequest filter);

    TransactionResponse getPayment(UUID paymentId);

    TransactionResponse initiatePayment(InitiateTransactionRequest request);

    TransactionResponse confirmPayment(UUID paymentId);

    TransactionResponse retryPayment(UUID paymentId);

    TransactionResponse cancelPayment(UUID paymentId);
}
