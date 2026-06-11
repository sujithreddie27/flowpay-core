package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AccountNotActiveException extends BaseException {

    public AccountNotActiveException(UUID accountId) {
        super(
                String.format("Account is not active: %s", accountId),
                "ACCOUNT_NOT_ACTIVE",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
