package com.flowpay.transaction.specification;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.entity.Transaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransactionSpecification {

    private TransactionSpecification() {
    }

    public static Specification<Transaction> withFilters(TransactionFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            }

            if (filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFromDate()));
            }

            if (filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getToDate()));
            }

            if (filter.getMinAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.getMinAmount()));
            }

            if (filter.getMaxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.getMaxAmount()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Transaction> userInvolved(UUID userId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("sender").get("id"), userId),
                cb.equal(root.get("receiver").get("id"), userId)
        );
    }

    public static Specification<Transaction> bySenderId(UUID senderId) {
        return (root, query, cb) -> cb.equal(root.get("sender").get("id"), senderId);
    }

    public static Specification<Transaction> byReceiverId(UUID receiverId) {
        return (root, query, cb) -> cb.equal(root.get("receiver").get("id"), receiverId);
    }

    public static Specification<Transaction> byStatus(TransactionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> byType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> byDateRange(OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> cb.between(root.get("createdAt"), from, to);
    }

    public static Specification<Transaction> byAmountRange(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> cb.between(root.get("amount"), min, max);
    }

    public static Specification<Transaction> completedInDateRange(OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), TransactionStatus.COMPLETED),
                cb.between(root.get("createdAt"), from, to)
        );
    }

    public static Specification<Transaction> userCompletedInDateRange(UUID userId, OffsetDateTime from, OffsetDateTime to) {
        return Specification.where(userInvolved(userId))
                .and(completedInDateRange(from, to));
    }
}
