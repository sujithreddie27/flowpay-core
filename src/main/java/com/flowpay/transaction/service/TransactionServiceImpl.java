package com.flowpay.transaction.service;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.exception.*;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.mapper.TransactionMapper;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.statemachine.TransactionStatusMachine;
import com.flowpay.transaction.validation.PaymentValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final int STALE_THRESHOLD_MINUTES = 30;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final PaymentValidationService paymentValidationService;
    private final TransactionMapper transactionMapper;
    private final TransactionStatusMachine statusMachine;
    private final FailedTransactionHandler failedTransactionHandler;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 150, multiplier = 2)
    )
    public TransactionResponse initiatePayment(InitiateTransactionRequest request) {
        log.info("Initiating payment: senderAccount={}, receiverAccount={}, amount={}, currency={}",
                request.getSenderAccountId(), request.getReceiverAccountId(),
                request.getAmount(), request.getCurrency());

        // 1. Idempotency check — return existing transaction if already processed
        if (request.getIdempotencyKey() != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate request detected for idempotencyKey={}, returning existing transaction={}",
                        request.getIdempotencyKey(), existing.get().getId());
                return transactionMapper.toResponse(existing.get());
            }
        }

        // 2. Load accounts with pessimistic lock to prevent concurrent modifications
        Account senderAccount = accountRepository.findByIdWithLock(request.getSenderAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getSenderAccountId()));
        Account receiverAccount = accountRepository.findByIdWithLock(request.getReceiverAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getReceiverAccountId()));

        // 3. Run validation chain (status, limits, currency, duplicates)
        paymentValidationService.validateAndThrow(request, senderAccount, receiverAccount);

        // 4. Create transaction record in PENDING status
        Transaction transaction = Transaction.builder()
                .referenceId(generateReferenceId())
                .idempotencyKey(request.getIdempotencyKey())
                .sender(senderAccount.getUser())
                .receiver(receiverAccount.getUser())
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Transaction created: id={}, referenceId={}, status=PENDING",
                transaction.getId(), transaction.getReferenceId());

        // 5. Process the transfer
        try {
            transitionStatus(transaction, TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Debit sender
            senderAccount.debit(request.getAmount());
            accountRepository.save(senderAccount);

            // Credit receiver
            receiverAccount.credit(request.getAmount());
            accountRepository.save(receiverAccount);

            // Mark completed
            transitionStatus(transaction, TransactionStatus.COMPLETED);
            transaction.setProcessedAt(OffsetDateTime.now());
            transaction = transactionRepository.save(transaction);

            log.info("Transaction completed: id={}, referenceId={}", transaction.getId(), transaction.getReferenceId());

        } catch (IllegalStateException e) {
            // Insufficient balance — permanent failure, no retry
            transitionStatus(transaction, TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transaction = transactionRepository.save(transaction);

            failedTransactionHandler.handlePermanentFailure(transaction, e);

            log.warn("Transaction failed: id={}, reason={}", transaction.getId(), e.getMessage());
            throw new InsufficientFundsException(request.getAmount(), senderAccount.getBalance());

        } catch (Exception e) {
            // Unexpected failure — attempt rollback and classify
            handleProcessingFailure(transaction, senderAccount, receiverAccount, request, e);
            throw new PaymentException("Transaction processing failed: " + e.getMessage(), e);
        }

        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse retryTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!statusMachine.isRetryable(transaction.getStatus())) {
            throw new TransactionNotRetryableException(transactionId,
                    "Transaction is in " + transaction.getStatus() + " state");
        }

        if (failedTransactionHandler.isRetryExhausted(transaction)) {
            throw new TransactionNotRetryableException(transactionId,
                    "Maximum retry attempts (" + transaction.getRetryCount() + ") exhausted");
        }

        log.info("Retrying transaction: id={}, attempt={}", transactionId, transaction.getRetryCount() + 1);

        // Transition back to PENDING for reprocessing
        transitionStatus(transaction, TransactionStatus.PENDING);
        transaction = transactionRepository.save(transaction);

        // Load accounts with pessimistic lock
        UUID senderAccId = transaction.getSenderAccount().getId();
        UUID receiverAccId = transaction.getReceiverAccount().getId();
        Account senderAccount = accountRepository.findByIdWithLock(senderAccId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", senderAccId));
        Account receiverAccount = accountRepository.findByIdWithLock(receiverAccId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", receiverAccId));

        try {
            transitionStatus(transaction, TransactionStatus.PROCESSING);
            transaction.incrementRetryCount();
            transactionRepository.save(transaction);

            // Debit sender
            senderAccount.debit(transaction.getAmount());
            accountRepository.save(senderAccount);

            // Credit receiver
            receiverAccount.credit(transaction.getAmount());
            accountRepository.save(receiverAccount);

            // Mark completed
            transitionStatus(transaction, TransactionStatus.COMPLETED);
            transaction.setProcessedAt(OffsetDateTime.now());
            transaction.setFailureReason(null);
            transaction = transactionRepository.save(transaction);

            log.info("Transaction retry successful: id={}, referenceId={}",
                    transaction.getId(), transaction.getReferenceId());

        } catch (Exception e) {
            handleProcessingFailure(transaction, senderAccount, receiverAccount, null, e);
            throw new PaymentException("Transaction retry failed: " + e.getMessage(), e);
        }

        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse reverseTransaction(UUID transactionId, String reason) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        statusMachine.validateTransition(transaction.getStatus(), TransactionStatus.REVERSED);

        // Load accounts with pessimistic lock
        UUID senderAccId = transaction.getSenderAccount().getId();
        UUID receiverAccId = transaction.getReceiverAccount().getId();
        Account senderAccount = accountRepository.findByIdWithLock(senderAccId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", senderAccId));
        Account receiverAccount = accountRepository.findByIdWithLock(receiverAccId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", receiverAccId));

        // Reverse: credit back to sender, debit from receiver
        receiverAccount.debit(transaction.getAmount());
        accountRepository.save(receiverAccount);

        senderAccount.credit(transaction.getAmount());
        accountRepository.save(senderAccount);

        transitionStatus(transaction, TransactionStatus.REVERSED);
        transaction.setFailureReason(reason);
        transaction.setProcessedAt(OffsetDateTime.now());
        transaction = transactionRepository.save(transaction);

        log.info("Transaction reversed: id={}, reason={}", transactionId, reason);
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getRetryableTransactions() {
        return transactionRepository.findRetryableTransactions()
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int processStalePendingTransactions() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<Transaction> stale = transactionRepository.findStalePendingTransactions(threshold);

        int failedCount = 0;
        for (Transaction transaction : stale) {
            try {
                transitionStatus(transaction, TransactionStatus.FAILED);
                transaction.setFailureReason("Transaction timed out after " + STALE_THRESHOLD_MINUTES + " minutes");
                transactionRepository.save(transaction);

                failedTransactionHandler.moveToDeadLetter(transaction,
                        new PaymentException("Stale pending transaction timeout"), false);
                failedCount++;

                log.warn("Stale transaction marked FAILED: id={}", transaction.getId());
            } catch (Exception e) {
                log.error("Error processing stale transaction: id={}", transaction.getId(), e);
            }
        }

        log.info("Processed {} stale pending transactions", failedCount);
        return failedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReferenceId(String referenceId) {
        Transaction transaction = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new TransactionNotFoundException(referenceId));
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByUserId(UUID userId, TransactionFilterRequest filter) {
        Pageable pageable = buildPageable(filter);
        Page<Transaction> transactions = transactionRepository.findByUserId(userId, pageable);
        return transactions.map(transactionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsBySenderId(UUID senderId, TransactionFilterRequest filter) {
        Pageable pageable = buildPageable(filter);
        Page<Transaction> transactions = transactionRepository.findBySenderId(senderId, pageable);
        return transactions.map(transactionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByReceiverId(UUID receiverId, TransactionFilterRequest filter) {
        Pageable pageable = buildPageable(filter);
        Page<Transaction> transactions = transactionRepository.findByReceiverId(receiverId, pageable);
        return transactions.map(transactionMapper::toResponse);
    }

    @Override
    @Transactional
    public TransactionResponse cancelTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        statusMachine.validateTransition(transaction.getStatus(), TransactionStatus.CANCELLED);

        transitionStatus(transaction, TransactionStatus.CANCELLED);
        transaction = transactionRepository.save(transaction);
        log.info("Transaction cancelled: id={}", transactionId);
        return transactionMapper.toResponse(transaction);
    }

    private void handleProcessingFailure(Transaction transaction, Account senderAccount,
                                         Account receiverAccount, InitiateTransactionRequest request,
                                         Exception e) {
        // Attempt rollback if sender was debited but receiver wasn't credited
        try {
            rollbackPartialTransfer(transaction, senderAccount, receiverAccount);
        } catch (Exception rollbackEx) {
            log.error("Rollback failed for transaction={}: {}",
                    transaction.getId(), rollbackEx.getMessage());
        }

        transitionStatus(transaction, TransactionStatus.FAILED);
        transaction.setFailureReason("Internal processing error");
        transaction.incrementRetryCount();
        transactionRepository.save(transaction);

        if (failedTransactionHandler.isPermanentFailure(e)) {
            failedTransactionHandler.handlePermanentFailure(transaction, e);
        } else {
            failedTransactionHandler.handleTransientFailure(transaction, e);
        }

        log.error("Transaction failed unexpectedly: id={}", transaction.getId(), e);
    }

    private void rollbackPartialTransfer(Transaction transaction, Account senderAccount, Account receiverAccount) {
        if (transaction.getStatus() == TransactionStatus.PROCESSING) {
            // Reload accounts to check current state
            Account currentSender = accountRepository.findByIdWithLock(senderAccount.getId()).orElse(null);
            Account currentReceiver = accountRepository.findByIdWithLock(receiverAccount.getId()).orElse(null);

            if (currentSender == null || currentReceiver == null) {
                log.warn("Cannot rollback - accounts not found for transaction={}", transaction.getId());
                return;
            }

            // If sender was debited (balance is lower than original), credit it back
            if (currentSender.getBalance().compareTo(senderAccount.getBalance()) < 0) {
                currentSender.credit(transaction.getAmount());
                accountRepository.save(currentSender);
                log.info("Rolled back sender debit for transaction={}", transaction.getId());
            }

            // If receiver was credited (balance is higher than original), debit it back
            if (currentReceiver.getBalance().compareTo(receiverAccount.getBalance()) > 0) {
                currentReceiver.debit(transaction.getAmount());
                accountRepository.save(currentReceiver);
                log.info("Rolled back receiver credit for transaction={}", transaction.getId());
            }
        }
    }

    private void transitionStatus(Transaction transaction, TransactionStatus newStatus) {
        statusMachine.validateTransition(transaction.getStatus(), newStatus);
        transaction.setStatus(newStatus);
    }

    private Pageable buildPageable(TransactionFilterRequest filter) {
        Sort sort = filter.getSortDirection().equalsIgnoreCase("ASC")
                ? Sort.by(filter.getSortBy()).ascending()
                : Sort.by(filter.getSortBy()).descending();
        return PageRequest.of(filter.getPage(), filter.getSize(), sort);
    }

    private String generateReferenceId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
