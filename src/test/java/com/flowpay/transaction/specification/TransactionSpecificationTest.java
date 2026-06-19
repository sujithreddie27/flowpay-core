package com.flowpay.transaction.specification;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.*;
import com.flowpay.transaction.dto.TransactionFilterRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSpecificationTest {

    @Mock
    private Root<Transaction> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> path;

    @Mock
    private Path<Object> nestedPath;

    @Mock
    private Predicate predicate;

    @Nested
    @DisplayName("withFilters")
    class WithFilters {

        @Test
        @DisplayName("should create empty predicate when no filters set")
        void shouldCreateEmptyPredicateWhenNoFilters() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder().build();
            when(cb.and(any(Predicate[].class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.withFilters(filter);
            Predicate result = spec.toPredicate(root, query, cb);

            assertThat(result).isNotNull();
            verify(cb).and(any(Predicate[].class));
        }

        @Test
        @DisplayName("should filter by status")
        void shouldFilterByStatus() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .status(TransactionStatus.COMPLETED)
                    .build();
            when(root.get("status")).thenReturn(path);
            when(cb.equal(path, TransactionStatus.COMPLETED)).thenReturn(predicate);
            when(cb.and(any(Predicate[].class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.withFilters(filter);
            spec.toPredicate(root, query, cb);

            verify(cb).equal(path, TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("should filter by type")
        void shouldFilterByType() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .type(TransactionType.TRANSFER)
                    .build();
            when(root.get("type")).thenReturn(path);
            when(cb.equal(path, TransactionType.TRANSFER)).thenReturn(predicate);
            when(cb.and(any(Predicate[].class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.withFilters(filter);
            spec.toPredicate(root, query, cb);

            verify(cb).equal(path, TransactionType.TRANSFER);
        }

        @Test
        @DisplayName("should filter by date range")
        void shouldFilterByDateRange() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .fromDate(from)
                    .toDate(to)
                    .build();
            when(root.get("createdAt")).thenReturn(path);
            when(cb.greaterThanOrEqualTo(any(), eq(from))).thenReturn(predicate);
            when(cb.lessThanOrEqualTo(any(), eq(to))).thenReturn(predicate);
            when(cb.and(any(Predicate[].class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.withFilters(filter);
            spec.toPredicate(root, query, cb);

            verify(cb).greaterThanOrEqualTo(any(), eq(from));
            verify(cb).lessThanOrEqualTo(any(), eq(to));
        }

        @Test
        @DisplayName("should filter by amount range")
        void shouldFilterByAmountRange() {
            BigDecimal min = new BigDecimal("10.00");
            BigDecimal max = new BigDecimal("1000.00");
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .minAmount(min)
                    .maxAmount(max)
                    .build();
            when(root.get("amount")).thenReturn(path);
            when(cb.greaterThanOrEqualTo(any(), eq(min))).thenReturn(predicate);
            when(cb.lessThanOrEqualTo(any(), eq(max))).thenReturn(predicate);
            when(cb.and(any(Predicate[].class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.withFilters(filter);
            spec.toPredicate(root, query, cb);

            verify(cb).greaterThanOrEqualTo(any(), eq(min));
            verify(cb).lessThanOrEqualTo(any(), eq(max));
        }

        @Test
        @DisplayName("should combine all filters")
        void shouldCombineAllFilters() {
            OffsetDateTime from = OffsetDateTime.now().minusDays(7);
            OffsetDateTime to = OffsetDateTime.now();
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .status(TransactionStatus.COMPLETED)
                    .type(TransactionType.TRANSFER)
                    .fromDate(from)
                    .toDate(to)
                    .minAmount(new BigDecimal("10.00"))
                    .maxAmount(new BigDecimal("1000.00"))
                    .build();
            when(root.get(anyString())).thenReturn(path);
            when(cb.equal(any(), any(TransactionStatus.class))).thenReturn(predicate);
            when(cb.equal(any(), any(TransactionType.class))).thenReturn(predicate);
            when(cb.greaterThanOrEqualTo(any(), any(OffsetDateTime.class))).thenReturn(predicate);
            when(cb.lessThanOrEqualTo(any(), any(OffsetDateTime.class))).thenReturn(predicate);
            when(cb.greaterThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(predicate);
            when(cb.lessThanOrEqualTo(any(), any(BigDecimal.class))).thenReturn(predicate);
            when(cb.and(any(Predicate[].class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.withFilters(filter);
            spec.toPredicate(root, query, cb);

            // All 6 filter conditions should be applied
            verify(cb).equal(any(), eq(TransactionStatus.COMPLETED));
            verify(cb).equal(any(), eq(TransactionType.TRANSFER));
            verify(cb).greaterThanOrEqualTo(any(), eq(from));
            verify(cb).lessThanOrEqualTo(any(), eq(to));
            verify(cb).greaterThanOrEqualTo(any(), eq(new BigDecimal("10.00")));
            verify(cb).lessThanOrEqualTo(any(), eq(new BigDecimal("1000.00")));
        }
    }

    @Nested
    @DisplayName("userInvolved")
    class UserInvolved {

        @Test
        @DisplayName("should create OR predicate for sender/receiver")
        void shouldCreateOrPredicateForUser() {
            UUID userId = UUID.randomUUID();
            when(root.get("sender")).thenReturn(path);
            when(root.get("receiver")).thenReturn(path);
            when(path.get("id")).thenReturn(nestedPath);
            when(cb.equal(nestedPath, userId)).thenReturn(predicate);
            when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.userInvolved(userId);
            spec.toPredicate(root, query, cb);

            verify(cb).or(any(Predicate.class), any(Predicate.class));
        }
    }

    @Nested
    @DisplayName("bySenderId")
    class BySenderId {

        @Test
        @DisplayName("should filter by sender ID")
        void shouldFilterBySenderId() {
            UUID senderId = UUID.randomUUID();
            when(root.get("sender")).thenReturn(path);
            when(path.get("id")).thenReturn(nestedPath);
            when(cb.equal(nestedPath, senderId)).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.bySenderId(senderId);
            spec.toPredicate(root, query, cb);

            verify(cb).equal(nestedPath, senderId);
        }
    }

    @Nested
    @DisplayName("byReceiverId")
    class ByReceiverId {

        @Test
        @DisplayName("should filter by receiver ID")
        void shouldFilterByReceiverId() {
            UUID receiverId = UUID.randomUUID();
            when(root.get("receiver")).thenReturn(path);
            when(path.get("id")).thenReturn(nestedPath);
            when(cb.equal(nestedPath, receiverId)).thenReturn(predicate);

            Specification<Transaction> spec = TransactionSpecification.byReceiverId(receiverId);
            spec.toPredicate(root, query, cb);

            verify(cb).equal(nestedPath, receiverId);
        }
    }
}
