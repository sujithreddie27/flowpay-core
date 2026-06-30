package com.flowpay.monitoring.metrics;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class PaymentMetricsService {

    private static final String METRIC_PREFIX = "payment";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeTransactionsGauge;

    public PaymentMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.activeTransactionsGauge = new AtomicInteger(0);

        Gauge.builder(METRIC_PREFIX + "_active_transactions_count", activeTransactionsGauge, AtomicInteger::get)
                .description("Number of currently active (in-progress) transactions")
                .register(meterRegistry);
    }

    /**
     * Increment the total transactions counter by status.
     */
    public void recordTransactionCompleted(TransactionStatus status, TransactionType type) {
        Counter.builder(METRIC_PREFIX + "_transactions_total")
                .description("Total number of payment transactions")
                .tag("status", status.name())
                .tag("type", type.name())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record payment processing duration.
     */
    public void recordProcessingDuration(long durationMs, TransactionStatus outcome, TransactionType type) {
        Timer.builder(METRIC_PREFIX + "_processing_duration_seconds")
                .description("Payment processing duration")
                .tag("outcome", outcome.name())
                .tag("type", type.name())
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * Record processing duration using a Timer.Sample.
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, TransactionStatus outcome, TransactionType type) {
        sample.stop(Timer.builder(METRIC_PREFIX + "_processing_duration_seconds")
                .description("Payment processing duration")
                .tag("outcome", outcome.name())
                .tag("type", type.name())
                .register(meterRegistry));
    }

    /**
     * Increment active transaction gauge when processing starts.
     */
    public void incrementActiveTransactions() {
        activeTransactionsGauge.incrementAndGet();
    }

    /**
     * Decrement active transaction gauge when processing finishes.
     */
    public void decrementActiveTransactions() {
        activeTransactionsGauge.decrementAndGet();
    }

    /**
     * Record a payment failure for failure rate tracking.
     */
    public void recordPaymentFailure(TransactionType type, String reason) {
        Counter.builder(METRIC_PREFIX + "_failures_total")
                .description("Total number of payment failures")
                .tag("type", type.name())
                .tag("reason", sanitizeReason(reason))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record account balance as a gauge observation.
     */
    public void recordAccountBalance(String accountId, String currency, BigDecimal balance) {
        Gauge.builder(METRIC_PREFIX + "_account_balance_total", balance, BigDecimal::doubleValue)
                .description("Current account balance")
                .tag("account_id", accountId)
                .tag("currency", currency)
                .register(meterRegistry);
    }

    /**
     * Record transaction amount distribution.
     */
    public void recordTransactionAmount(BigDecimal amount, TransactionType type) {
        DistributionSummary.builder(METRIC_PREFIX + "_transaction_amount")
                .description("Distribution of transaction amounts")
                .tag("type", type.name())
                .baseUnit("currency_unit")
                .register(meterRegistry)
                .record(amount.doubleValue());
    }

    /**
     * Record retry attempts.
     */
    public void recordRetryAttempt(String transactionId) {
        Counter.builder(METRIC_PREFIX + "_retry_attempts_total")
                .description("Total number of transaction retry attempts")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record reversal events.
     */
    public void recordReversal(TransactionType type) {
        Counter.builder(METRIC_PREFIX + "_reversals_total")
                .description("Total number of transaction reversals")
                .tag("type", type.name())
                .register(meterRegistry)
                .increment();
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        // Limit cardinality by categorizing reasons
        String lower = reason.toLowerCase();
        if (lower.contains("insufficient")) return "insufficient_funds";
        if (lower.contains("timeout") || lower.contains("timed out")) return "timeout";
        if (lower.contains("validation")) return "validation_error";
        if (lower.contains("account") && lower.contains("active")) return "account_inactive";
        return "internal_error";
    }
}
