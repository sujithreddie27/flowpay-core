package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BaseException {

    public InsufficientFundsException(String message) {
        super(message, "INSUFFICIENT_FUNDS", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public InsufficientFundsException(java.math.BigDecimal requested, java.math.BigDecimal available) {
        super(
                String.format("Insufficient funds: requested %s but only %s available", requested, available),
                "INSUFFICIENT_FUNDS",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
