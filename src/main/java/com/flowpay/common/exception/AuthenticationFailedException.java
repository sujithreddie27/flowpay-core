package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationFailedException extends BaseException {

    public AuthenticationFailedException(String message) {
        super(message, "AUTHENTICATION_FAILED", HttpStatus.UNAUTHORIZED);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, "AUTHENTICATION_FAILED", HttpStatus.UNAUTHORIZED, cause);
    }
}
