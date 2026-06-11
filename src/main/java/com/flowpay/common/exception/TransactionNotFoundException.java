package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TransactionNotFoundException extends BaseException {

    public TransactionNotFoundException(UUID transactionId) {
        super(
                String.format("Transaction not found with id: %s", transactionId),
                "TRANSACTION_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }

    public TransactionNotFoundException(String referenceId) {
        super(
                String.format("Transaction not found with reference: %s", referenceId),
                "TRANSACTION_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }
}
