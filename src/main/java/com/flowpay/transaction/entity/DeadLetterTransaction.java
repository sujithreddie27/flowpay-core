package com.flowpay.transaction.entity;

import com.flowpay.common.enums.TransactionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_transactions", indexes = {
        @Index(name = "idx_dlt_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_dlt_status", columnList = "status"),
        @Index(name = "idx_dlt_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @NotBlank
    @Size(max = 64)
    @Column(name = "reference_id", nullable = false, length = 64)
    private String referenceId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "original_status", nullable = false, length = 20)
    private TransactionStatus originalStatus;

    @NotBlank
    @Size(max = 30)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "UNRESOLVED";

    @NotBlank
    @Size(max = 1000)
    @Column(name = "failure_reason", nullable = false, length = 1000)
    private String failureReason;

    @Size(max = 255)
    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "stack_trace", length = 10000)
    private String stackTrace;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries_exhausted", nullable = false)
    @Builder.Default
    private Boolean maxRetriesExhausted = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Size(max = 1000)
    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public void markResolved(String notes, UUID resolvedByUserId) {
        this.status = "RESOLVED";
        this.resolutionNotes = notes;
        this.resolvedBy = resolvedByUserId;
        this.resolvedAt = OffsetDateTime.now();
    }

    public void markDiscarded(String notes, UUID resolvedByUserId) {
        this.status = "DISCARDED";
        this.resolutionNotes = notes;
        this.resolvedBy = resolvedByUserId;
        this.resolvedAt = OffsetDateTime.now();
    }

    public boolean isUnresolved() {
        return "UNRESOLVED".equals(this.status);
    }
}
