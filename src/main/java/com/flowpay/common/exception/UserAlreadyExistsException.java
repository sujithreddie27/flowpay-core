package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends BaseException {

    public UserAlreadyExistsException(String field, String value) {
        super(
                String.format("User already exists with %s: %s", field, value),
                "USER_ALREADY_EXISTS",
                HttpStatus.CONFLICT
        );
    }
}
