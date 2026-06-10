package com.flowpay.transaction.entity;

import com.flowpay.auth.entity.User;
import com.flowpay.common.entity.BaseEntity;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Transaction entity representing a financial transaction between accounts.
 * Supports idempotency through unique reference IDs and idempotency keys.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_sender_id", columnList = "sender_id"),
        @Index(name = "idx_transactions_receiver_id", columnList = "receiver_id"),
        @Index(name = "idx_transactions_status", columnList = "status"),
        @Index(name = "idx_transactions_type", columnList = "type"),
        @Index(name = "idx_transactions_created_at", columnList = "created_at"),
        @Index(name = "idx_transactions_reference_id", columnList = "reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    @NotBlank
    @Size(max = 64)
    @Column(name = "reference_id", nullable = false, unique = true, length = 64)
    private String referenceId;

    @Size(max = 64)
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transactions_sender"))
    @NotNull
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transactions_receiver"))
    @NotNull
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transactions_sender_account"))
    @NotNull
    private Account senderAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_transactions_receiver_account"))
    @NotNull
    private Account receiverAccount;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull
    @Column(name = "fee", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    @NotNull
    private TransactionType type;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @Size(max = 500)
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    /**
     * Check if transaction is in a terminal state.
     */
    public boolean isTerminal() {
        return status == TransactionStatus.COMPLETED ||
               status == TransactionStatus.FAILED ||
               status == TransactionStatus.CANCELLED;
    }

    /**
     * Check if transaction can be retried.
     */
    public boolean canRetry() {
        return status == TransactionStatus.FAILED && retryCount < 3;
    }

    /**
     * Mark transaction as completed.
     */
    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.processedAt = OffsetDateTime.now();
    }

    /**
     * Mark transaction as failed with a reason.
     */
    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = OffsetDateTime.now();
    }

    /**
     * Increment retry count.
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Get total transaction amount including fees.
     */
    public BigDecimal getTotalAmount() {
        return amount.add(fee);
    }
}
