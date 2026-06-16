package com.flowpay.transaction.validation;

import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.entity.Account;

public interface PaymentValidator {

    ValidationResult validate(PaymentValidationContext context);

    int getOrder();

    @lombok.Getter
    @lombok.Builder
    @lombok.AllArgsConstructor
    class PaymentValidationContext {
        private final InitiateTransactionRequest request;
        private final Account senderAccount;
        private final Account receiverAccount;
    }
}
