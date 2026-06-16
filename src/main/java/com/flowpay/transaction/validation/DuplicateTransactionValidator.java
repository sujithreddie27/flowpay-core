package com.flowpay.transaction.validation;

import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DuplicateTransactionValidator implements PaymentValidator {

    private final TransactionRepository transactionRepository;

    @Override
    public ValidationResult validate(PaymentValidationContext context) {
        String idempotencyKey = context.getRequest().getIdempotencyKey();

        if (!StringUtils.hasText(idempotencyKey)) {
            return ValidationResult.success();
        }

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return ValidationResult.failure(
                    "DUPLICATE_TRANSACTION",
                    String.format("A transaction with idempotency key '%s' already exists", idempotencyKey)
            );
        }

        return ValidationResult.success();
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
