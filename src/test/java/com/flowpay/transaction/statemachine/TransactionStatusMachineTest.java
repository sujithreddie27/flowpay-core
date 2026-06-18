package com.flowpay.transaction.statemachine;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class TransactionStatusMachineTest {

    private TransactionStatusMachine statusMachine;

    @BeforeEach
    void setUp() {
        statusMachine = new TransactionStatusMachine();
    }

    @Nested
    @DisplayName("PENDING transitions")
    class PendingTransitions {

        @Test
        @DisplayName("should allow PENDING → PROCESSING")
        void shouldAllowPendingToProcessing() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PENDING, TransactionStatus.PROCESSING));
        }

        @Test
        @DisplayName("should allow PENDING → CANCELLED")
        void shouldAllowPendingToCancelled() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PENDING, TransactionStatus.CANCELLED));
        }

        @Test
        @DisplayName("should allow PENDING → FAILED")
        void shouldAllowPendingToFailed() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PENDING, TransactionStatus.FAILED));
        }

        @Test
        @DisplayName("should reject PENDING → COMPLETED")
        void shouldRejectPendingToCompleted() {
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PENDING, TransactionStatus.COMPLETED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("should reject PENDING → REVERSED")
        void shouldRejectPendingToReversed() {
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PENDING, TransactionStatus.REVERSED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("PROCESSING transitions")
    class ProcessingTransitions {

        @Test
        @DisplayName("should allow PROCESSING → COMPLETED")
        void shouldAllowProcessingToCompleted() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PROCESSING, TransactionStatus.COMPLETED));
        }

        @Test
        @DisplayName("should allow PROCESSING → FAILED")
        void shouldAllowProcessingToFailed() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PROCESSING, TransactionStatus.FAILED));
        }

        @Test
        @DisplayName("should reject PROCESSING → PENDING")
        void shouldRejectProcessingToPending() {
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.PROCESSING, TransactionStatus.PENDING))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("COMPLETED transitions")
    class CompletedTransitions {

        @Test
        @DisplayName("should allow COMPLETED → REVERSED")
        void shouldAllowCompletedToReversed() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.COMPLETED, TransactionStatus.REVERSED));
        }

        @Test
        @DisplayName("should reject COMPLETED → FAILED")
        void shouldRejectCompletedToFailed() {
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.COMPLETED, TransactionStatus.FAILED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("should reject COMPLETED → PENDING")
        void shouldRejectCompletedToPending() {
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.COMPLETED, TransactionStatus.PENDING))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("FAILED transitions")
    class FailedTransitions {

        @Test
        @DisplayName("should allow FAILED → PENDING (retry)")
        void shouldAllowFailedToPending() {
            assertThatNoException().isThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.FAILED, TransactionStatus.PENDING));
        }

        @Test
        @DisplayName("should reject FAILED → COMPLETED")
        void shouldRejectFailedToCompleted() {
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.FAILED, TransactionStatus.COMPLETED))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Terminal state transitions")
    class TerminalStates {

        @ParameterizedTest
        @EnumSource(TransactionStatus.class)
        @DisplayName("should reject all transitions from REVERSED")
        void shouldRejectAllTransitionsFromReversed(TransactionStatus target) {
            if (target == TransactionStatus.REVERSED) return;
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.REVERSED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(TransactionStatus.class)
        @DisplayName("should reject all transitions from CANCELLED")
        void shouldRejectAllTransitionsFromCancelled(TransactionStatus target) {
            if (target == TransactionStatus.CANCELLED) return;
            assertThatThrownBy(() ->
                    statusMachine.validateTransition(TransactionStatus.CANCELLED, target))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("canTransition")
    class CanTransition {

        @Test
        @DisplayName("should return true for valid transition")
        void shouldReturnTrueForValid() {
            assertThat(statusMachine.canTransition(TransactionStatus.PENDING, TransactionStatus.PROCESSING)).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid transition")
        void shouldReturnFalseForInvalid() {
            assertThat(statusMachine.canTransition(TransactionStatus.COMPLETED, TransactionStatus.PENDING)).isFalse();
        }
    }

    @Nested
    @DisplayName("getAllowedTransitions")
    class GetAllowedTransitions {

        @Test
        @DisplayName("should return correct allowed transitions from PENDING")
        void shouldReturnAllowedFromPending() {
            Set<TransactionStatus> allowed = statusMachine.getAllowedTransitions(TransactionStatus.PENDING);
            assertThat(allowed).containsExactlyInAnyOrder(
                    TransactionStatus.PROCESSING, TransactionStatus.CANCELLED, TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("should return empty set for terminal states")
        void shouldReturnEmptyForTerminal() {
            assertThat(statusMachine.getAllowedTransitions(TransactionStatus.REVERSED)).isEmpty();
            assertThat(statusMachine.getAllowedTransitions(TransactionStatus.CANCELLED)).isEmpty();
        }
    }

    @Nested
    @DisplayName("isTerminal")
    class IsTerminal {

        @Test
        @DisplayName("should return true for COMPLETED, REVERSED, CANCELLED")
        void shouldReturnTrueForTerminalStates() {
            assertThat(statusMachine.isTerminal(TransactionStatus.COMPLETED)).isTrue();
            assertThat(statusMachine.isTerminal(TransactionStatus.REVERSED)).isTrue();
            assertThat(statusMachine.isTerminal(TransactionStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-terminal states")
        void shouldReturnFalseForNonTerminal() {
            assertThat(statusMachine.isTerminal(TransactionStatus.PENDING)).isFalse();
            assertThat(statusMachine.isTerminal(TransactionStatus.PROCESSING)).isFalse();
            assertThat(statusMachine.isTerminal(TransactionStatus.FAILED)).isFalse();
        }
    }

    @Nested
    @DisplayName("isRetryable")
    class IsRetryable {

        @Test
        @DisplayName("should return true only for FAILED")
        void shouldReturnTrueForFailed() {
            assertThat(statusMachine.isRetryable(TransactionStatus.FAILED)).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = TransactionStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("should return false for non-FAILED states")
        void shouldReturnFalseForOthers(TransactionStatus status) {
            assertThat(statusMachine.isRetryable(status)).isFalse();
        }
    }
}
