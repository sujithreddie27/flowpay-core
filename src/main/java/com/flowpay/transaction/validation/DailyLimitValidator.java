package com.flowpay.transaction.validation;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class DailyLimitValidator implements PaymentValidator {

    private final TransactionRepository transactionRepository;

    @Value("${flowpay.payment.daily-limit-multiplier:1.0}")
    private double dailyLimitMultiplier;

    @Override
    public ValidationResult validate(PaymentValidationContext context) {
        var senderAccount = context.getSenderAccount();
        BigDecimal dailyLimit = senderAccount.getDailyLimit();

        OffsetDateTime startOfDay = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endOfDay = startOfDay.plusDays(1);

        BigDecimal totalSentToday = transactionRepository.getTotalCommittedAmountBySenderIdAndDateRange(
                senderAccount.getUser().getId(), startOfDay, endOfDay);

        BigDecimal effectiveLimit = dailyLimit.multiply(BigDecimal.valueOf(dailyLimitMultiplier));
        BigDecimal projectedTotal = totalSentToday.add(context.getRequest().getAmount());

        if (projectedTotal.compareTo(effectiveLimit) > 0) {
            BigDecimal remaining = effectiveLimit.subtract(totalSentToday);
            return ValidationResult.failure(
                    "DAILY_LIMIT_EXCEEDED",
                    String.format("Transaction would exceed daily limit. Limit: %s, already spent: %s, remaining: %s",
                            effectiveLimit.toPlainString(), totalSentToday.toPlainString(),
                            remaining.max(BigDecimal.ZERO).toPlainString())
            );
        }

        return ValidationResult.success();
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
