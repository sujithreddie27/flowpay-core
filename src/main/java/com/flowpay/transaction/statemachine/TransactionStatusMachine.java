package com.flowpay.transaction.statemachine;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class TransactionStatusMachine {

    private final Map<TransactionStatus, Set<TransactionStatus>> transitions;

    public TransactionStatusMachine() {
        transitions = new EnumMap<>(TransactionStatus.class);
        transitions.put(TransactionStatus.PENDING, EnumSet.of(
                TransactionStatus.PROCESSING,
                TransactionStatus.CANCELLED,
                TransactionStatus.FAILED
        ));
        transitions.put(TransactionStatus.PROCESSING, EnumSet.of(
                TransactionStatus.COMPLETED,
                TransactionStatus.FAILED
        ));
        transitions.put(TransactionStatus.COMPLETED, EnumSet.of(
                TransactionStatus.REVERSED
        ));
        transitions.put(TransactionStatus.FAILED, EnumSet.of(
                TransactionStatus.PENDING
        ));
        transitions.put(TransactionStatus.REVERSED, EnumSet.noneOf(TransactionStatus.class));
        transitions.put(TransactionStatus.CANCELLED, EnumSet.noneOf(TransactionStatus.class));
    }

    public void validateTransition(TransactionStatus from, TransactionStatus to) {
        Set<TransactionStatus> allowed = transitions.getOrDefault(from, EnumSet.noneOf(TransactionStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidStateTransitionException(from, to);
        }
    }

    public boolean canTransition(TransactionStatus from, TransactionStatus to) {
        Set<TransactionStatus> allowed = transitions.getOrDefault(from, EnumSet.noneOf(TransactionStatus.class));
        return allowed.contains(to);
    }

    public Set<TransactionStatus> getAllowedTransitions(TransactionStatus from) {
        return transitions.getOrDefault(from, EnumSet.noneOf(TransactionStatus.class));
    }

    public boolean isTerminal(TransactionStatus status) {
        return status == TransactionStatus.COMPLETED ||
               status == TransactionStatus.REVERSED ||
               status == TransactionStatus.CANCELLED;
    }

    public boolean isRetryable(TransactionStatus status) {
        return status == TransactionStatus.FAILED;
    }
}
