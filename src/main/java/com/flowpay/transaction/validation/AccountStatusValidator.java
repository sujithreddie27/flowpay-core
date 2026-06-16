package com.flowpay.transaction.validation;

import com.flowpay.common.enums.AccountStatus;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import org.springframework.stereotype.Component;

@Component
public class AccountStatusValidator implements PaymentValidator {

    @Override
    public ValidationResult validate(PaymentValidationContext context) {
        Account senderAccount = context.getSenderAccount();
        Account receiverAccount = context.getReceiverAccount();

        if (senderAccount.getStatus() == AccountStatus.FROZEN) {
            return ValidationResult.failure(
                    "SENDER_ACCOUNT_FROZEN",
                    String.format("Sender account %s is frozen and cannot initiate transactions",
                            senderAccount.getAccountNumber())
            );
        }

        if (senderAccount.getStatus() == AccountStatus.CLOSED) {
            return ValidationResult.failure(
                    "SENDER_ACCOUNT_CLOSED",
                    String.format("Sender account %s is closed", senderAccount.getAccountNumber())
            );
        }

        if (!senderAccount.isActive()) {
            return ValidationResult.failure(
                    "SENDER_ACCOUNT_NOT_ACTIVE",
                    String.format("Sender account %s is not in active status", senderAccount.getAccountNumber())
            );
        }

        if (receiverAccount.getStatus() == AccountStatus.CLOSED) {
            return ValidationResult.failure(
                    "RECEIVER_ACCOUNT_CLOSED",
                    String.format("Receiver account %s is closed and cannot receive funds",
                            receiverAccount.getAccountNumber())
            );
        }

        if (receiverAccount.getStatus() == AccountStatus.FROZEN) {
            return ValidationResult.failure(
                    "RECEIVER_ACCOUNT_FROZEN",
                    String.format("Receiver account %s is frozen and cannot receive funds",
                            receiverAccount.getAccountNumber())
            );
        }

        return ValidationResult.success();
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
