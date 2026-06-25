package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends BaseException {

    public InvalidTokenException(String message) {
        super(message, "INVALID_TOKEN", HttpStatus.UNAUTHORIZED);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, "INVALID_TOKEN", HttpStatus.UNAUTHORIZED, cause);
    }
}
