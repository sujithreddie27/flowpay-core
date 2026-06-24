package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException(String key) {
        super("Rate limit exceeded for: " + key, "RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS);
    }
}
