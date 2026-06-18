package com.flowpay.transaction.service;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.AccountType;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.exception.AccountNotActiveException;
import com.flowpay.common.exception.InsufficientFundsException;
import com.flowpay.common.exception.InvalidStateTransitionException;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.DeadLetterTransaction;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.DeadLetterTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailedTransactionHandlerTest {

    @Mock
    private DeadLetterTransactionRepository deadLetterTransactionRepository;

    @InjectMocks
    private FailedTransactionHandler failedTransactionHandler;

    @Captor
    private ArgumentCaptor<DeadLetterTransaction> dltCaptor;

    private Transaction transaction;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID();

        User sender = User.builder()
                .email("sender@test.com")
                .firstName("Sender")
                .lastName("User")
                .passwordHash("hashed")
                .build();
        sender.setId(UUID.randomUUID());

        User receiver = User.builder()
                .email("receiver@test.com")
                .firstName("Receiver")
                .lastName("User")
                .passwordHash("hashed")
                .build();
        receiver.setId(UUID.randomUUID());

        Account senderAccount = Account.builder()
                .user(sender)
                .accountNumber("1000000001")
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .build();
        senderAccount.setId(UUID.randomUUID());

        Account receiverAccount = Account.builder()
                .user(receiver)
                .accountNumber("1000000002")
                .balance(new BigDecimal("2000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .build();
        receiverAccount.setId(UUID.randomUUID());

        transaction = Transaction.builder()
                .referenceId("TXN-TEST123")
                .sender(sender)
                .receiver(receiver)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.FAILED)
                .retryCount(0)
                .build();
        transaction.setId(transactionId);
    }

    @Nested
    @DisplayName("handleTransientFailure")
    class HandleTransientFailure {

        @Test
        @DisplayName("should increment retry count and set failure status")
        void shouldIncrementRetryCount() {
            RuntimeException ex = new RuntimeException("Connection timeout");
            failedTransactionHandler.handleTransientFailure(transaction, ex);

            assertThat(transaction.getRetryCount()).isEqualTo(1);
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(transaction.getFailureReason()).isEqualTo("Connection timeout");
        }

        @Test
        @DisplayName("should move to dead letter when max retries exhausted")
        void shouldMoveToDeadLetterOnMaxRetries() {
            transaction.setRetryCount(2); // Will become 3 after increment
            when(deadLetterTransactionRepository.existsByTransactionId(transactionId)).thenReturn(false);
            when(deadLetterTransactionRepository.save(any(DeadLetterTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            RuntimeException ex = new RuntimeException("Persistent timeout");
            failedTransactionHandler.handleTransientFailure(transaction, ex);

            verify(deadLetterTransactionRepository).save(dltCaptor.capture());
            DeadLetterTransaction dlt = dltCaptor.getValue();
            assertThat(dlt.getTransactionId()).isEqualTo(transactionId);
            assertThat(dlt.getMaxRetriesExhausted()).isTrue();
            assertThat(dlt.getExceptionClass()).isEqualTo("java.lang.RuntimeException");
        }

        @Test
        @DisplayName("should not move to dead letter when retries remain")
        void shouldNotMoveToDeadLetterWhenRetriesRemain() {
            transaction.setRetryCount(0);

            RuntimeException ex = new RuntimeException("Transient error");
            failedTransactionHandler.handleTransientFailure(transaction, ex);

            verify(deadLetterTransactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("handlePermanentFailure")
    class HandlePermanentFailure {

        @Test
        @DisplayName("should move to dead letter immediately")
        void shouldMoveToDeadLetterImmediately() {
            when(deadLetterTransactionRepository.existsByTransactionId(transactionId)).thenReturn(false);
            when(deadLetterTransactionRepository.save(any(DeadLetterTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            InsufficientFundsException ex = new InsufficientFundsException(
                    new BigDecimal("500.00"), new BigDecimal("100.00"));
            failedTransactionHandler.handlePermanentFailure(transaction, ex);

            verify(deadLetterTransactionRepository).save(dltCaptor.capture());
            DeadLetterTransaction dlt = dltCaptor.getValue();
            assertThat(dlt.getTransactionId()).isEqualTo(transactionId);
            assertThat(dlt.getMaxRetriesExhausted()).isFalse();
            assertThat(dlt.getExceptionClass()).isEqualTo(
                    "com.flowpay.common.exception.InsufficientFundsException");
        }

        @Test
        @DisplayName("should skip if already in dead letter")
        void shouldSkipIfAlreadyInDeadLetter() {
            when(deadLetterTransactionRepository.existsByTransactionId(transactionId)).thenReturn(true);

            RuntimeException ex = new RuntimeException("Error");
            failedTransactionHandler.handlePermanentFailure(transaction, ex);

            verify(deadLetterTransactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("isPermanentFailure")
    class IsPermanentFailure {

        @Test
        @DisplayName("should classify InsufficientFundsException as permanent")
        void shouldClassifyInsufficientFundsAsPermanent() {
            assertThat(failedTransactionHandler.isPermanentFailure(
                    new InsufficientFundsException("Not enough"))).isTrue();
        }

        @Test
        @DisplayName("should classify AccountNotActiveException as permanent")
        void shouldClassifyAccountNotActiveAsPermanent() {
            assertThat(failedTransactionHandler.isPermanentFailure(
                    new AccountNotActiveException(UUID.randomUUID()))).isTrue();
        }

        @Test
        @DisplayName("should classify InvalidStateTransitionException as permanent")
        void shouldClassifyInvalidStateTransitionAsPermanent() {
            assertThat(failedTransactionHandler.isPermanentFailure(
                    new InvalidStateTransitionException(TransactionStatus.COMPLETED, TransactionStatus.PENDING))).isTrue();
        }

        @Test
        @DisplayName("should classify IllegalArgumentException as permanent")
        void shouldClassifyIllegalArgumentAsPermanent() {
            assertThat(failedTransactionHandler.isPermanentFailure(
                    new IllegalArgumentException("Bad arg"))).isTrue();
        }

        @Test
        @DisplayName("should classify RuntimeException as transient")
        void shouldClassifyRuntimeExceptionAsTransient() {
            assertThat(failedTransactionHandler.isPermanentFailure(
                    new RuntimeException("Generic error"))).isFalse();
        }
    }

    @Nested
    @DisplayName("isRetryExhausted")
    class IsRetryExhausted {

        @Test
        @DisplayName("should return true when retryCount >= 3")
        void shouldReturnTrueWhenMaxReached() {
            transaction.setRetryCount(3);
            assertThat(failedTransactionHandler.isRetryExhausted(transaction)).isTrue();
        }

        @Test
        @DisplayName("should return false when retries remain")
        void shouldReturnFalseWhenRetriesRemain() {
            transaction.setRetryCount(1);
            assertThat(failedTransactionHandler.isRetryExhausted(transaction)).isFalse();
        }
    }

    @Nested
    @DisplayName("moveToDeadLetter metadata")
    class MoveToDeadLetterMetadata {

        @Test
        @DisplayName("should capture correct metadata in dead letter entry")
        void shouldCaptureCorrectMetadata() {
            when(deadLetterTransactionRepository.existsByTransactionId(transactionId)).thenReturn(false);
            when(deadLetterTransactionRepository.save(any(DeadLetterTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Exception ex = new RuntimeException("Test error");
            failedTransactionHandler.moveToDeadLetter(transaction, ex, true);

            verify(deadLetterTransactionRepository).save(dltCaptor.capture());
            DeadLetterTransaction dlt = dltCaptor.getValue();

            assertThat(dlt.getReferenceId()).isEqualTo("TXN-TEST123");
            assertThat(dlt.getOriginalStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(dlt.getFailureReason()).isEqualTo("Test error");
            assertThat(dlt.getStackTrace()).isNotNull();
            assertThat(dlt.getMetadata()).containsKeys("senderId", "receiverId", "amount", "currency");
            assertThat(dlt.getMetadata().get("amount")).isEqualTo("100.00");
            assertThat(dlt.getMetadata().get("currency")).isEqualTo("USD");
        }
    }
}
