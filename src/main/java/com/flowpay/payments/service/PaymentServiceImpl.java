package com.flowpay.payments.service;

import com.flowpay.common.dto.PagedResponse;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.exception.InvalidStateTransitionException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.payments.dto.PaymentFilterRequest;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.mapper.TransactionMapper;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionService transactionService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listPayments(UUID userId, PaymentFilterRequest filter) {
        log.debug("Listing payments for user: {}", userId);
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Transaction> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            predicates = cb.and(predicates, cb.equal(root.get("type"), TransactionType.PAYMENT));
            predicates = cb.and(predicates, cb.or(
                    cb.equal(root.get("sender").get("id"), userId),
                    cb.equal(root.get("receiver").get("id"), userId)
            ));

            if (filter.getStatus() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getFromDate() != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }
            return predicates;
        };

        Page<Transaction> page = transactionRepository.findAll(spec, pageable);
        Page<TransactionResponse> responsePage = page.map(transactionMapper::toResponse);
        return PagedResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getPayment(UUID paymentId) {
        Transaction transaction = transactionRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse initiatePayment(InitiateTransactionRequest request) {
        request.setType(TransactionType.PAYMENT);
        return transactionService.initiatePayment(request);
    }

    @Override
    @Transactional
    public TransactionResponse confirmPayment(UUID paymentId) {
        log.info("Confirming payment: {}", paymentId);
        Transaction transaction = transactionRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    transaction.getStatus(), TransactionStatus.PROCESSING);
        }

        // Transition to FAILED first so retryTransaction can process it
        // (retryTransaction handles FAILED → PENDING → PROCESSING → COMPLETED)
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason("Awaiting confirmation");
        transactionRepository.save(transaction);

        return transactionService.retryTransaction(paymentId);
    }

    @Override
    @Transactional
    public TransactionResponse retryPayment(UUID paymentId) {
        log.info("Retrying payment: {}", paymentId);
        return transactionService.retryTransaction(paymentId);
    }

    @Override
    @Transactional
    public TransactionResponse cancelPayment(UUID paymentId) {
        log.info("Cancelling payment: {}", paymentId);
        return transactionService.cancelTransaction(paymentId);
    }
}
