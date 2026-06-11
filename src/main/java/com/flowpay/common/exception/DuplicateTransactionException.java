package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTransactionException extends BaseException {

    public DuplicateTransactionException(String idempotencyKey) {
        super(
                String.format("Duplicate transaction detected for idempotency key: %s", idempotencyKey),
                "DUPLICATE_TRANSACTION",
                HttpStatus.CONFLICT
        );
    }
}
