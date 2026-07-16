package com.flowpay.admin.service;

import com.flowpay.admin.dto.*;
import com.flowpay.auth.dto.UserResponse;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.mapper.UserMapper;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.dto.AuditLogFilterRequest;
import com.flowpay.common.dto.AuditLogResponse;
import com.flowpay.common.dto.PagedResponse;
import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.UserStatus;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.common.repository.AuditLogRepository;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.mapper.AccountMapper;
import com.flowpay.transaction.mapper.TransactionMapper;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.service.TransactionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserMapper userMapper;
    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final TransactionService transactionService;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        log.debug("Fetching admin dashboard stats");

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.findByStatus(UserStatus.ACTIVE).size();
        long totalTransactions = transactionRepository.count();
        long pendingTransactions = transactionRepository.countByStatus(TransactionStatus.PENDING);
        long failedTransactions = transactionRepository.countByStatus(TransactionStatus.FAILED);
        long completedTransactions = transactionRepository.countByStatus(TransactionStatus.COMPLETED);

        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        BigDecimal totalVolume = transactionRepository.findByDateRange(thirtyDaysAgo, OffsetDateTime.now())
                .stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFees = transactionRepository.findByDateRange(thirtyDaysAgo, OffsetDateTime.now())
                .stream()
                .filter(t -> t.getStatus() == TransactionStatus.COMPLETED)
                .map(Transaction::getFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalTransactions(totalTransactions)
                .pendingTransactions(pendingTransactions)
                .failedTransactions(failedTransactions)
                .completedTransactions(completedTransactions)
                .totalVolume(totalVolume)
                .totalFees(totalFees)
                .build();
    }

    @Override
    public ProcessingRateResponse getProcessingRate() {
        Timer timer = meterRegistry.find("payment.initiate.duration").timer();
        double currentTps = 0.0;
        if (timer != null && timer.count() > 0) {
            double totalSeconds = timer.totalTime(TimeUnit.SECONDS);
            currentTps = totalSeconds > 0 ? timer.count() / totalSeconds : 0;
        }

        return ProcessingRateResponse.builder()
                .currentTps(currentTps)
                .dataPoints(List.of())
                .build();
    }

    @Override
    public LatencyResponse getLatency() {
        Timer timer = meterRegistry.find("payment.initiate.duration").timer();
        if (timer == null || timer.count() == 0) {
            return LatencyResponse.builder()
                    .p50Ms(0)
                    .p95Ms(0)
                    .p99Ms(0)
                    .meanMs(0)
                    .maxMs(0)
                    .build();
        }

        var snapshot = timer.takeSnapshot();
        double p50 = 0, p95 = 0, p99 = 0;
        for (var pv : snapshot.percentileValues()) {
            if (Double.compare(pv.percentile(), 0.5) == 0) p50 = pv.value(TimeUnit.MILLISECONDS);
            if (Double.compare(pv.percentile(), 0.95) == 0) p95 = pv.value(TimeUnit.MILLISECONDS);
            if (Double.compare(pv.percentile(), 0.99) == 0) p99 = pv.value(TimeUnit.MILLISECONDS);
        }

        return LatencyResponse.builder()
                .p50Ms(p50)
                .p95Ms(p95)
                .p99Ms(p99)
                .meanMs(timer.mean(TimeUnit.MILLISECONDS))
                .maxMs(timer.max(TimeUnit.MILLISECONDS))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findAll(pageable);
        Page<UserResponse> responsePage = userPage.map(userMapper::toResponse);
        return PagedResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<AccountResponse> accounts = accountRepository.findByUserId(userId)
                .stream()
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());

        long totalTransactions = transactionRepository.countBySenderId(userId);

        return AdminUserDetailResponse.builder()
                .user(userMapper.toResponse(user))
                .accounts(accounts)
                .totalTransactions(totalTransactions)
                .build();
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        log.info("Admin updating user status: userId={}, newStatus={}", userId, request.getStatus());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setStatus(request.getStatus());
        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(TransactionFilterRequest filter) {
        Pageable pageable = PageRequest.of(
                filter.getPage(), filter.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Transaction> spec = buildTransactionSpec(filter);
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);
        Page<TransactionResponse> responsePage = page.map(transactionMapper::toResponse);
        return PagedResponse.from(responsePage);
    }

    @Override
    @Transactional
    public TransactionResponse overrideTransactionStatus(UUID transactionId, OverrideTransactionStatusRequest request) {
        log.info("Admin overriding transaction status: transactionId={}, newStatus={}", transactionId, request.getStatus());
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));

        transaction.setStatus(request.getStatus());
        if (request.getStatus() == TransactionStatus.COMPLETED) {
            transaction.setProcessedAt(OffsetDateTime.now());
        }
        if (request.getReason() != null) {
            transaction.setFailureReason(request.getReason());
        }

        transaction = transactionRepository.save(transaction);
        return transactionMapper.toResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> getAuditLog(AuditLogFilterRequest filter) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> page;
        if (filter.getEntityType() != null && filter.getEntityId() != null) {
            page = auditLogRepository.findByEntityTypeAndEntityId(filter.getEntityType(), filter.getEntityId(), pageable);
        } else if (filter.getAction() != null) {
            page = auditLogRepository.findByAction(filter.getAction(), pageable);
        } else if (filter.getPerformedBy() != null) {
            page = auditLogRepository.findByPerformedById(filter.getPerformedBy(), pageable);
        } else if (filter.getFromDate() != null && filter.getToDate() != null) {
            page = auditLogRepository.findByDateRange(filter.getFromDate(), filter.getToDate(), pageable);
        } else {
            page = auditLogRepository.findAll(pageable);
        }

        Page<AuditLogResponse> responsePage = page.map(this::mapAuditLog);
        return PagedResponse.from(responsePage);
    }

    @Override
    @Transactional
    public BulkRetryResponse bulkRetryTransactions(BulkRetryRequest request) {
        log.info("Bulk retrying {} transactions", request.getTransactionIds().size());
        List<UUID> failedIds = new ArrayList<>();
        int successCount = 0;

        for (UUID transactionId : request.getTransactionIds()) {
            try {
                transactionService.retryTransaction(transactionId);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to retry transaction: {}, reason: {}", transactionId, e.getMessage());
                failedIds.add(transactionId);
            }
        }

        return BulkRetryResponse.builder()
                .totalRequested(request.getTransactionIds().size())
                .successCount(successCount)
                .failedCount(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    private AuditLogResponse mapAuditLog(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .action(auditLog.getAction())
                .oldValue(auditLog.getOldValue())
                .newValue(auditLog.getNewValue())
                .performedBy(auditLog.getPerformedBy() != null ? auditLog.getPerformedBy().getId() : null)
                .ipAddress(auditLog.getIpAddress())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    private Specification<Transaction> buildTransactionSpec(TransactionFilterRequest filter) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (filter.getStatus() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getType() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("type"), filter.getType()));
            }
            if (filter.getFromDate() != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }
            return predicates;
        };
    }
}
