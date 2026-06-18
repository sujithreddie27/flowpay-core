package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotRetryableException extends BaseException {

    public TransactionNotRetryableException(UUID transactionId, String reason) {
        super(
                String.format("Transaction %s cannot be retried: %s", transactionId, reason),
                "TRANSACTION_NOT_RETRYABLE",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
