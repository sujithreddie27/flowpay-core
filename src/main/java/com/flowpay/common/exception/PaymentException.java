package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends BaseException {

    public PaymentException(String message) {
        super(message, "PAYMENT_ERROR", HttpStatus.BAD_REQUEST);
    }

    public PaymentException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.BAD_REQUEST);
    }

    public PaymentException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, "PAYMENT_ERROR", HttpStatus.BAD_REQUEST, cause);
    }
}
