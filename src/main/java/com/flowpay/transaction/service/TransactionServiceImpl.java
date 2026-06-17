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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final PaymentValidationService paymentValidationService;
    private final TransactionMapper transactionMapper;

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
            transaction.setStatus(TransactionStatus.PROCESSING);
            transactionRepository.save(transaction);

            // Debit sender
            senderAccount.debit(request.getAmount());
            accountRepository.save(senderAccount);

            // Credit receiver
            receiverAccount.credit(request.getAmount());
            accountRepository.save(receiverAccount);

            // Mark completed
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setProcessedAt(OffsetDateTime.now());
            transaction = transactionRepository.save(transaction);

            log.info("Transaction completed: id={}, referenceId={}", transaction.getId(), transaction.getReferenceId());

        } catch (IllegalStateException e) {
            // Insufficient balance — caught from Account.debit()
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transaction = transactionRepository.save(transaction);
            log.warn("Transaction failed: id={}, reason={}", transaction.getId(), e.getMessage());
            throw new InsufficientFundsException(request.getAmount(), senderAccount.getBalance());

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Internal processing error");
            transaction.setRetryCount(transaction.getRetryCount() + 1);
            transaction = transactionRepository.save(transaction);
            log.error("Transaction failed unexpectedly: id={}", transaction.getId(), e);
            throw new PaymentException("Transaction processing failed: " + e.getMessage(), e);
        }

        return transactionMapper.toResponse(transaction);
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

        if (transaction.isTerminal()) {
            throw new PaymentException(
                    "Cannot cancel transaction in terminal state: " + transaction.getStatus(),
                    "INVALID_STATE_TRANSITION"
            );
        }

        transaction.setStatus(TransactionStatus.CANCELLED);
        transaction = transactionRepository.save(transaction);
        log.info("Transaction cancelled: id={}", transactionId);
        return transactionMapper.toResponse(transaction);
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
