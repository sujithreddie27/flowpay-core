package com.flowpay.transaction.service;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.TransactionNotFoundException;
import com.flowpay.kafka.producer.PaymentEventProducer;
import com.flowpay.monitoring.metrics.PaymentMetricsService;
import com.flowpay.transaction.dto.*;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.mapper.TransactionMapper;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.statemachine.TransactionStatusMachine;
import com.flowpay.transaction.validation.PaymentValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
class TransactionHistoryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentValidationService paymentValidationService;

    @Mock
    private TransactionMapper transactionMapper;

    @Spy
    private TransactionStatusMachine statusMachine = new TransactionStatusMachine();

    @Mock
    private FailedTransactionHandler failedTransactionHandler;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private PaymentMetricsService paymentMetricsService;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UUID senderUserId;
    private UUID receiverUserId;
    private UUID senderAccountId;
    private UUID receiverAccountId;
    private User senderUser;
    private User receiverUser;
    private Account senderAccount;
    private Account receiverAccount;
    private Transaction completedTransaction;
    private Transaction failedTransaction;
    private TransactionResponse completedResponse;

    @BeforeEach
    void setUp() {
        senderUserId = UUID.randomUUID();
        receiverUserId = UUID.randomUUID();
        senderAccountId = UUID.randomUUID();
        receiverAccountId = UUID.randomUUID();

        senderUser = User.builder()
                .email("sender@example.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("hashed")
                .build();
        senderUser.setId(senderUserId);

        receiverUser = User.builder()
                .email("receiver@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .passwordHash("hashed")
                .build();
        receiverUser.setId(receiverUserId);

        senderAccount = Account.builder()
                .user(senderUser)
                .accountNumber("1234567890")
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        senderAccount.setId(senderAccountId);

        receiverAccount = Account.builder()
                .user(receiverUser)
                .accountNumber("0987654321")
                .balance(new BigDecimal("2000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        receiverAccount.setId(receiverAccountId);

        completedTransaction = Transaction.builder()
                .referenceId("TXN-COMPLETED001")
                .sender(senderUser)
                .receiver(receiverUser)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .fee(new BigDecimal("2.50"))
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.TRANSFER)
                .description("Test transfer")
                .processedAt(OffsetDateTime.now())
                .build();
        completedTransaction.setId(UUID.randomUUID());
        completedTransaction.setCreatedAt(OffsetDateTime.now().minusHours(2));

        failedTransaction = Transaction.builder()
                .referenceId("TXN-FAILED001")
                .sender(senderUser)
                .receiver(receiverUser)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .fee(BigDecimal.ZERO)
                .status(TransactionStatus.FAILED)
                .type(TransactionType.PAYMENT)
                .description("Failed payment")
                .failureReason("Insufficient funds")
                .build();
        failedTransaction.setId(UUID.randomUUID());
        failedTransaction.setCreatedAt(OffsetDateTime.now().minusHours(1));

        completedResponse = TransactionResponse.builder()
                .id(completedTransaction.getId())
                .referenceId("TXN-COMPLETED001")
                .senderId(senderUserId)
                .receiverId(receiverUserId)
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .fee(new BigDecimal("2.50"))
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.TRANSFER)
                .createdAt(completedTransaction.getCreatedAt())
                .build();
    }

    @Nested
    @DisplayName("getTransactionHistory")
    class GetTransactionHistory {

        @Test
        @DisplayName("should return paginated filtered transactions")
        void shouldReturnPaginatedFilteredTransactions() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .status(TransactionStatus.COMPLETED)
                    .type(TransactionType.TRANSFER)
                    .page(0)
                    .size(10)
                    .sortBy("createdAt")
                    .sortDirection("DESC")
                    .build();

            Page<Transaction> transactionPage = new PageImpl<>(List.of(completedTransaction));
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(transactionPage);
            when(transactionMapper.toResponse(completedTransaction)).thenReturn(completedResponse);

            Page<TransactionResponse> result = transactionService.getTransactionHistory(filter);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("should return empty page when no transactions match")
        void shouldReturnEmptyPageWhenNoMatch() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .status(TransactionStatus.REVERSED)
                    .build();

            Page<Transaction> emptyPage = Page.empty();
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<TransactionResponse> result = transactionService.getTransactionHistory(filter);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should apply date range filter")
        void shouldApplyDateRangeFilter() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .fromDate(OffsetDateTime.now().minusDays(7))
                    .toDate(OffsetDateTime.now())
                    .build();

            Page<Transaction> page = new PageImpl<>(List.of(completedTransaction));
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);
            when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(completedResponse);

            Page<TransactionResponse> result = transactionService.getTransactionHistory(filter);

            assertThat(result.getContent()).hasSize(1);
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("should apply amount range filter")
        void shouldApplyAmountRangeFilter() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .minAmount(new BigDecimal("100.00"))
                    .maxAmount(new BigDecimal("1000.00"))
                    .build();

            Page<Transaction> page = new PageImpl<>(List.of(completedTransaction));
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);
            when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(completedResponse);

            Page<TransactionResponse> result = transactionService.getTransactionHistory(filter);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getTransactionsByUserId with Specification")
    class GetTransactionsByUserIdWithSpec {

        @Test
        @DisplayName("should combine user filter with additional filters")
        void shouldCombineUserFilterWithAdditionalFilters() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .status(TransactionStatus.COMPLETED)
                    .minAmount(new BigDecimal("100.00"))
                    .build();

            Page<Transaction> page = new PageImpl<>(List.of(completedTransaction));
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);
            when(transactionMapper.toResponse(completedTransaction)).thenReturn(completedResponse);

            Page<TransactionResponse> result = transactionService.getTransactionsByUserId(senderUserId, filter);

            assertThat(result.getContent()).hasSize(1);
            verify(transactionRepository).findAll(any(Specification.class), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("getTransactionSummary")
    class GetTransactionSummary {

        @Test
        @DisplayName("should return complete summary for user")
        void shouldReturnCompleteSummaryForUser() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(30);
            OffsetDateTime to = OffsetDateTime.now();

            when(transactionRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of(completedTransaction, failedTransaction));

            TransactionSummaryResponse summary = transactionService.getTransactionSummary(senderUserId, from, to);

            assertThat(summary.getTotalTransactions()).isEqualTo(2);
            assertThat(summary.getCompletedTransactions()).isEqualTo(1);
            assertThat(summary.getFailedTransactions()).isEqualTo(1);
            assertThat(summary.getTotalAmountSent()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(summary.getTotalAmountReceived()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getTotalFees()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(summary.getNetFlow()).isEqualByComparingTo(new BigDecimal("-500.00"));
            assertThat(summary.getPeriodStart()).isEqualTo(from);
            assertThat(summary.getPeriodEnd()).isEqualTo(to);
        }

        @Test
        @DisplayName("should calculate net flow for receiver")
        void shouldCalculateNetFlowForReceiver() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(30);
            OffsetDateTime to = OffsetDateTime.now();

            when(transactionRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of(completedTransaction));

            TransactionSummaryResponse summary = transactionService.getTransactionSummary(receiverUserId, from, to);

            assertThat(summary.getTotalTransactions()).isEqualTo(1);
            assertThat(summary.getCompletedTransactions()).isEqualTo(1);
            assertThat(summary.getTotalAmountSent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getTotalAmountReceived()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(summary.getNetFlow()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("should return empty summary when no transactions")
        void shouldReturnEmptySummaryWhenNoTransactions() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(30);
            OffsetDateTime to = OffsetDateTime.now();

            when(transactionRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of());

            TransactionSummaryResponse summary = transactionService.getTransactionSummary(senderUserId, from, to);

            assertThat(summary.getTotalTransactions()).isZero();
            assertThat(summary.getCompletedTransactions()).isZero();
            assertThat(summary.getFailedTransactions()).isZero();
            assertThat(summary.getTotalAmountSent()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getTotalAmountReceived()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getTransactionsByType()).isEmpty();
            assertThat(summary.getTransactionsByStatus()).isEmpty();
        }

        @Test
        @DisplayName("should group transactions by type and status")
        void shouldGroupTransactionsByTypeAndStatus() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(30);
            OffsetDateTime to = OffsetDateTime.now();

            when(transactionRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of(completedTransaction, failedTransaction));

            TransactionSummaryResponse summary = transactionService.getTransactionSummary(senderUserId, from, to);

            assertThat(summary.getTransactionsByType())
                    .containsEntry(TransactionType.TRANSFER, 1L)
                    .containsEntry(TransactionType.PAYMENT, 1L);
            assertThat(summary.getTransactionsByStatus())
                    .containsEntry(TransactionStatus.COMPLETED, 1L)
                    .containsEntry(TransactionStatus.FAILED, 1L);
        }
    }

    @Nested
    @DisplayName("getTransactionReceipt")
    class GetTransactionReceipt {

        @Test
        @DisplayName("should return full receipt details")
        void shouldReturnFullReceiptDetails() {
            when(transactionRepository.findById(completedTransaction.getId()))
                    .thenReturn(Optional.of(completedTransaction));

            TransactionReceiptResponse receipt = transactionService.getTransactionReceipt(completedTransaction.getId());

            assertThat(receipt.getTransactionId()).isEqualTo(completedTransaction.getId());
            assertThat(receipt.getReferenceId()).isEqualTo("TXN-COMPLETED001");
            assertThat(receipt.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(receipt.getType()).isEqualTo(TransactionType.TRANSFER);
            assertThat(receipt.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(receipt.getCurrency()).isEqualTo("USD");
            assertThat(receipt.getFee()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(receipt.getTotalAmount()).isEqualByComparingTo(new BigDecimal("502.50"));
            assertThat(receipt.getSenderName()).isEqualTo("John Doe");
            assertThat(receipt.getReceiverName()).isEqualTo("Jane Smith");
            assertThat(receipt.getSenderAccountNumber()).isEqualTo("******7890");
            assertThat(receipt.getReceiverAccountNumber()).isEqualTo("******4321");
            assertThat(receipt.getDescription()).isEqualTo("Test transfer");
        }

        @Test
        @DisplayName("should throw exception when transaction not found")
        void shouldThrowWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(transactionRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getTransactionReceipt(unknownId))
                    .isInstanceOf(TransactionNotFoundException.class);
        }

        @Test
        @DisplayName("should include failure reason for failed transactions")
        void shouldIncludeFailureReasonForFailedTransaction() {
            when(transactionRepository.findById(failedTransaction.getId()))
                    .thenReturn(Optional.of(failedTransaction));

            TransactionReceiptResponse receipt = transactionService.getTransactionReceipt(failedTransaction.getId());

            assertThat(receipt.getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(receipt.getFailureReason()).isEqualTo("Insufficient funds");
        }

        @Test
        @DisplayName("should mask account numbers showing only last 4 digits")
        void shouldMaskAccountNumbers() {
            when(transactionRepository.findById(completedTransaction.getId()))
                    .thenReturn(Optional.of(completedTransaction));

            TransactionReceiptResponse receipt = transactionService.getTransactionReceipt(completedTransaction.getId());

            assertThat(receipt.getSenderAccountNumber()).startsWith("*");
            assertThat(receipt.getSenderAccountNumber()).endsWith("7890");
            assertThat(receipt.getReceiverAccountNumber()).startsWith("*");
            assertThat(receipt.getReceiverAccountNumber()).endsWith("4321");
        }
    }
}
