package com.flowpay.transaction.service;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.*;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.mapper.TransactionMapper;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.validation.PaymentValidationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentValidationService paymentValidationService;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    private UUID senderAccountId;
    private UUID receiverAccountId;
    private UUID senderUserId;
    private UUID receiverUserId;
    private User senderUser;
    private User receiverUser;
    private Account senderAccount;
    private Account receiverAccount;
    private InitiateTransactionRequest request;
    private TransactionResponse expectedResponse;

    @BeforeEach
    void setUp() {
        senderAccountId = UUID.randomUUID();
        receiverAccountId = UUID.randomUUID();
        senderUserId = UUID.randomUUID();
        receiverUserId = UUID.randomUUID();

        senderUser = User.builder()
                .email("sender@example.com")
                .firstName("Sender")
                .lastName("User")
                .passwordHash("hashed")
                .build();
        senderUser.setId(senderUserId);

        receiverUser = User.builder()
                .email("receiver@example.com")
                .firstName("Receiver")
                .lastName("User")
                .passwordHash("hashed")
                .build();
        receiverUser.setId(receiverUserId);

        senderAccount = Account.builder()
                .user(senderUser)
                .accountNumber("1000000001")
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        senderAccount.setId(senderAccountId);
        senderAccount.setCreatedAt(OffsetDateTime.now());
        senderAccount.setUpdatedAt(OffsetDateTime.now());

        receiverAccount = Account.builder()
                .user(receiverUser)
                .accountNumber("1000000002")
                .balance(new BigDecimal("2000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        receiverAccount.setId(receiverAccountId);
        receiverAccount.setCreatedAt(OffsetDateTime.now());
        receiverAccount.setUpdatedAt(OffsetDateTime.now());

        request = InitiateTransactionRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .description("Test transfer")
                .idempotencyKey("idem-key-123")
                .build();

        expectedResponse = TransactionResponse.builder()
                .id(UUID.randomUUID())
                .referenceId("TXN-ABC123")
                .senderId(senderUserId)
                .receiverId(receiverUserId)
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .fee(BigDecimal.ZERO)
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.TRANSFER)
                .description("Test transfer")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePayment {

        @Test
        @DisplayName("should process payment successfully")
        void shouldProcessPaymentSuccessfully() {
            List<TransactionStatus> capturedStatuses = new java.util.ArrayList<>();

            when(transactionRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());
            when(accountRepository.findByIdWithLock(senderAccountId))
                    .thenReturn(Optional.of(senderAccount));
            when(accountRepository.findByIdWithLock(receiverAccountId))
                    .thenReturn(Optional.of(receiverAccount));
            doNothing().when(paymentValidationService)
                    .validateAndThrow(request, senderAccount, receiverAccount);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> {
                        Transaction t = invocation.getArgument(0);
                        capturedStatuses.add(t.getStatus());
                        if (t.getId() == null) {
                            t.setId(UUID.randomUUID());
                        }
                        return t;
                    });
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionMapper.toResponse(any(Transaction.class)))
                    .thenReturn(expectedResponse);

            TransactionResponse result = transactionService.initiatePayment(request);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            // Verify transaction was saved 3 times with correct state transitions
            verify(transactionRepository, times(3)).save(any(Transaction.class));
            assertThat(capturedStatuses).containsExactly(
                    TransactionStatus.PENDING,
                    TransactionStatus.PROCESSING,
                    TransactionStatus.COMPLETED
            );

            // Verify accounts were debited and credited
            verify(accountRepository, times(2)).save(any(Account.class));
        }

        @Test
        @DisplayName("should return existing transaction for duplicate idempotency key")
        void shouldReturnExistingForDuplicateIdempotencyKey() {
            Transaction existingTransaction = Transaction.builder()
                    .referenceId("TXN-EXISTING")
                    .idempotencyKey("idem-key-123")
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            existingTransaction.setId(UUID.randomUUID());

            when(transactionRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.of(existingTransaction));
            when(transactionMapper.toResponse(existingTransaction))
                    .thenReturn(expectedResponse);

            TransactionResponse result = transactionService.initiatePayment(request);

            assertThat(result).isNotNull();
            verify(accountRepository, never()).findByIdWithLock(any());
            verify(paymentValidationService, never()).validateAndThrow(any(), any(), any());
        }

        @Test
        @DisplayName("should skip idempotency check when key is null")
        void shouldSkipIdempotencyCheckWhenKeyIsNull() {
            InitiateTransactionRequest noKeyRequest = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .build();

            when(accountRepository.findByIdWithLock(senderAccountId))
                    .thenReturn(Optional.of(senderAccount));
            when(accountRepository.findByIdWithLock(receiverAccountId))
                    .thenReturn(Optional.of(receiverAccount));
            doNothing().when(paymentValidationService)
                    .validateAndThrow(noKeyRequest, senderAccount, receiverAccount);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> {
                        Transaction t = invocation.getArgument(0);
                        if (t.getId() == null) t.setId(UUID.randomUUID());
                        return t;
                    });
            when(accountRepository.save(any(Account.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionMapper.toResponse(any(Transaction.class)))
                    .thenReturn(expectedResponse);

            TransactionResponse result = transactionService.initiatePayment(noKeyRequest);

            assertThat(result).isNotNull();
            verify(transactionRepository, never()).findByIdempotencyKey(any());
        }

        @Test
        @DisplayName("should throw when sender account not found")
        void shouldThrowWhenSenderAccountNotFound() {
            when(transactionRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());
            when(accountRepository.findByIdWithLock(senderAccountId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.initiatePayment(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when receiver account not found")
        void shouldThrowWhenReceiverAccountNotFound() {
            when(transactionRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());
            when(accountRepository.findByIdWithLock(senderAccountId))
                    .thenReturn(Optional.of(senderAccount));
            when(accountRepository.findByIdWithLock(receiverAccountId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.initiatePayment(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when validation fails")
        void shouldThrowWhenValidationFails() {
            when(transactionRepository.findByIdempotencyKey("idem-key-123"))
                    .thenReturn(Optional.empty());
            when(accountRepository.findByIdWithLock(senderAccountId))
                    .thenReturn(Optional.of(senderAccount));
            when(accountRepository.findByIdWithLock(receiverAccountId))
                    .thenReturn(Optional.of(receiverAccount));
            doThrow(new PaymentException("Validation failed", "VALIDATION_ERROR"))
                    .when(paymentValidationService)
                    .validateAndThrow(request, senderAccount, receiverAccount);

            assertThatThrownBy(() -> transactionService.initiatePayment(request))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Validation failed");
        }

        @Test
        @DisplayName("should mark transaction FAILED when insufficient balance")
        void shouldMarkFailedWhenInsufficientBalance() {
            Account lowBalanceAccount = Account.builder()
                    .user(senderUser)
                    .accountNumber("1000000001")
                    .balance(new BigDecimal("10.00"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .dailyLimit(new BigDecimal("10000.0000"))
                    .build();
            lowBalanceAccount.setId(senderAccountId);

            InitiateTransactionRequest largeRequest = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .idempotencyKey("idem-key-456")
                    .build();

            when(transactionRepository.findByIdempotencyKey("idem-key-456"))
                    .thenReturn(Optional.empty());
            when(accountRepository.findByIdWithLock(senderAccountId))
                    .thenReturn(Optional.of(lowBalanceAccount));
            when(accountRepository.findByIdWithLock(receiverAccountId))
                    .thenReturn(Optional.of(receiverAccount));
            doNothing().when(paymentValidationService)
                    .validateAndThrow(largeRequest, lowBalanceAccount, receiverAccount);
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> {
                        Transaction t = invocation.getArgument(0);
                        if (t.getId() == null) t.setId(UUID.randomUUID());
                        return t;
                    });

            assertThatThrownBy(() -> transactionService.initiatePayment(largeRequest))
                    .isInstanceOf(InsufficientFundsException.class);

            verify(transactionRepository, atLeast(3)).save(transactionCaptor.capture());
            List<Transaction> saved = transactionCaptor.getAllValues();
            Transaction failedTxn = saved.get(saved.size() - 1);
            assertThat(failedTxn.getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(failedTxn.getFailureReason()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getTransactionById")
    class GetTransactionById {

        @Test
        @DisplayName("should return transaction by ID")
        void shouldReturnTransactionById() {
            UUID txnId = UUID.randomUUID();
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-TEST")
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(txnId);

            when(transactionRepository.findById(txnId)).thenReturn(Optional.of(transaction));
            when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

            TransactionResponse result = transactionService.getTransactionById(txnId);

            assertThat(result).isNotNull();
            verify(transactionRepository).findById(txnId);
        }

        @Test
        @DisplayName("should throw when transaction not found")
        void shouldThrowWhenNotFound() {
            UUID txnId = UUID.randomUUID();
            when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getTransactionById(txnId))
                    .isInstanceOf(TransactionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTransactionByReferenceId")
    class GetTransactionByReferenceId {

        @Test
        @DisplayName("should return transaction by reference ID")
        void shouldReturnByReferenceId() {
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-REF123")
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(UUID.randomUUID());

            when(transactionRepository.findByReferenceId("TXN-REF123"))
                    .thenReturn(Optional.of(transaction));
            when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

            TransactionResponse result = transactionService.getTransactionByReferenceId("TXN-REF123");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw when reference not found")
        void shouldThrowWhenReferenceNotFound() {
            when(transactionRepository.findByReferenceId("NONEXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getTransactionByReferenceId("NONEXISTENT"))
                    .isInstanceOf(TransactionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getTransactionsByUserId")
    class GetTransactionsByUserId {

        @Test
        @DisplayName("should return paginated transactions for user")
        void shouldReturnPaginatedTransactions() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder().build();
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-USER")
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(UUID.randomUUID());

            Page<Transaction> page = new PageImpl<>(List.of(transaction));
            when(transactionRepository.findByUserId(eq(senderUserId), any(Pageable.class)))
                    .thenReturn(page);
            when(transactionMapper.toResponse(transaction)).thenReturn(expectedResponse);

            Page<TransactionResponse> result = transactionService.getTransactionsByUserId(senderUserId, filter);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("cancelTransaction")
    class CancelTransaction {

        @Test
        @DisplayName("should cancel pending transaction")
        void shouldCancelPendingTransaction() {
            UUID txnId = UUID.randomUUID();
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-CANCEL")
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.PENDING)
                    .build();
            transaction.setId(txnId);

            TransactionResponse cancelledResponse = TransactionResponse.builder()
                    .id(txnId)
                    .status(TransactionStatus.CANCELLED)
                    .build();

            when(transactionRepository.findById(txnId)).thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(cancelledResponse);

            TransactionResponse result = transactionService.cancelTransaction(txnId);

            assertThat(result.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
            verify(transactionRepository).save(transactionCaptor.capture());
            assertThat(transactionCaptor.getValue().getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        }

        @Test
        @DisplayName("should throw when cancelling completed transaction")
        void shouldThrowWhenCancellingCompletedTransaction() {
            UUID txnId = UUID.randomUUID();
            Transaction transaction = Transaction.builder()
                    .referenceId("TXN-DONE")
                    .sender(senderUser)
                    .receiver(receiverUser)
                    .senderAccount(senderAccount)
                    .receiverAccount(receiverAccount)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transaction.setId(txnId);

            when(transactionRepository.findById(txnId)).thenReturn(Optional.of(transaction));

            assertThatThrownBy(() -> transactionService.cancelTransaction(txnId))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Cannot cancel transaction in terminal state");
        }

        @Test
        @DisplayName("should throw when transaction not found for cancellation")
        void shouldThrowWhenNotFoundForCancellation() {
            UUID txnId = UUID.randomUUID();
            when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.cancelTransaction(txnId))
                    .isInstanceOf(TransactionNotFoundException.class);
        }
    }
}
