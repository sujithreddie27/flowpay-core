package com.flowpay.transaction.validation;

import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AmountLimitValidator implements PaymentValidator {

    private final BigDecimal minimumAmount;
    private final BigDecimal maximumAmount;

    public AmountLimitValidator(
            @Value("${flowpay.payment.min-amount:0.01}") BigDecimal minimumAmount,
            @Value("${flowpay.payment.max-amount:1000000.00}") BigDecimal maximumAmount) {
        this.minimumAmount = minimumAmount;
        this.maximumAmount = maximumAmount;
    }

    @Override
    public ValidationResult validate(PaymentValidationContext context) {
        BigDecimal amount = context.getRequest().getAmount();

        if (amount.compareTo(minimumAmount) < 0) {
            return ValidationResult.failure(
                    "AMOUNT_BELOW_MINIMUM",
                    String.format("Transaction amount %s is below the minimum allowed amount of %s",
                            amount.toPlainString(), minimumAmount.toPlainString())
            );
        }

        if (amount.compareTo(maximumAmount) > 0) {
            return ValidationResult.failure(
                    "AMOUNT_ABOVE_MAXIMUM",
                    String.format("Transaction amount %s exceeds the maximum allowed amount of %s",
                            amount.toPlainString(), maximumAmount.toPlainString())
            );
        }

        return ValidationResult.success();
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
