package com.flowpay.transaction.service;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.transaction.entity.DeadLetterTransaction;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.DeadLetterTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedTransactionHandler {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int STACK_TRACE_MAX_LENGTH = 4000;

    private final DeadLetterTransactionRepository deadLetterTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransientFailure(Transaction transaction, Exception ex) {
        transaction.incrementRetryCount();
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(truncate(ex.getMessage(), 500));

        log.warn("Transient failure for transaction={}, retryCount={}/{}: {}",
                transaction.getId(), transaction.getRetryCount(), MAX_RETRY_ATTEMPTS, ex.getMessage());

        if (transaction.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            log.error("Max retries exhausted for transaction={}, moving to dead letter",
                    transaction.getId());
            moveToDeadLetter(transaction, ex, true);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePermanentFailure(Transaction transaction, Exception ex) {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(truncate(ex.getMessage(), 500));

        log.error("Permanent failure for transaction={}: {}", transaction.getId(), ex.getMessage());
        moveToDeadLetter(transaction, ex, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void moveToDeadLetter(Transaction transaction, Exception ex, boolean maxRetriesExhausted) {
        if (deadLetterTransactionRepository.existsByTransactionId(transaction.getId())) {
            log.info("Transaction={} already in dead letter queue, skipping", transaction.getId());
            return;
        }

        DeadLetterTransaction dlt = DeadLetterTransaction.builder()
                .transactionId(transaction.getId())
                .referenceId(transaction.getReferenceId())
                .originalStatus(transaction.getStatus())
                .failureReason(truncate(ex.getMessage(), 1000))
                .exceptionClass(ex.getClass().getName())
                .stackTrace(truncate(getStackTrace(ex), STACK_TRACE_MAX_LENGTH))
                .retryCount(transaction.getRetryCount())
                .maxRetriesExhausted(maxRetriesExhausted)
                .metadata(Map.of(
                        "senderId", String.valueOf(transaction.getSender().getId()),
                        "receiverId", String.valueOf(transaction.getReceiver().getId()),
                        "amount", transaction.getAmount().toPlainString(),
                        "currency", transaction.getCurrency()
                ))
                .build();

        deadLetterTransactionRepository.save(dlt);

        log.info("Transaction={} moved to dead letter queue, dltId={}",
                transaction.getId(), dlt.getId());
    }

    public boolean isRetryExhausted(Transaction transaction) {
        return transaction.getRetryCount() >= MAX_RETRY_ATTEMPTS;
    }

    public boolean isPermanentFailure(Exception ex) {
        return ex instanceof IllegalArgumentException ||
               ex instanceof IllegalStateException ||
               ex instanceof com.flowpay.common.exception.InsufficientFundsException ||
               ex instanceof com.flowpay.common.exception.AccountNotActiveException ||
               ex instanceof com.flowpay.common.exception.InvalidStateTransitionException;
    }

    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
