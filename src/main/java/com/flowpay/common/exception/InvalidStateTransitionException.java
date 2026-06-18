package com.flowpay.common.exception;

import com.flowpay.common.enums.TransactionStatus;
import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends BaseException {

    public InvalidStateTransitionException(TransactionStatus from, TransactionStatus to) {
        super(
                String.format("Invalid state transition from %s to %s", from, to),
                "INVALID_STATE_TRANSITION",
                HttpStatus.CONFLICT
        );
    }
}
