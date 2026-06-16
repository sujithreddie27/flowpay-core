package com.flowpay.transaction.validation;

import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import org.springframework.stereotype.Component;

@Component
public class CurrencyMismatchValidator implements PaymentValidator {

    @Override
    public ValidationResult validate(PaymentValidationContext context) {
        String requestCurrency = context.getRequest().getCurrency();
        Account senderAccount = context.getSenderAccount();
        Account receiverAccount = context.getReceiverAccount();

        if (!senderAccount.getCurrency().equalsIgnoreCase(requestCurrency)) {
            return ValidationResult.failure(
                    "SENDER_CURRENCY_MISMATCH",
                    String.format("Transaction currency %s does not match sender account currency %s",
                            requestCurrency, senderAccount.getCurrency())
            );
        }

        if (!receiverAccount.getCurrency().equalsIgnoreCase(requestCurrency)) {
            return ValidationResult.failure(
                    "RECEIVER_CURRENCY_MISMATCH",
                    String.format("Transaction currency %s does not match receiver account currency %s",
                            requestCurrency, receiverAccount.getCurrency())
            );
        }

        return ValidationResult.success();
    }

    @Override
    public int getOrder() {
        return 15;
    }
}
