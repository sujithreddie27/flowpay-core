package com.flowpay.transaction.entity;

import com.flowpay.auth.entity.User;
import com.flowpay.common.entity.BaseEntity;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Account entity representing a user's financial account.
 * Implements optimistic locking for concurrent balance updates.
 */
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_user_id", columnList = "user_id"),
        @Index(name = "idx_accounts_status", columnList = "status"),
        @Index(name = "idx_accounts_account_number", columnList = "account_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_accounts_user"))
    @NotNull
    private User user;

    @NotBlank
    @Size(max = 20)
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private AccountType accountType = AccountType.SAVINGS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @NotNull
    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("10000.0000");

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Check if account is active and can be used for transactions.
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Check if account has sufficient balance for a transaction.
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    /**
     * Credit the account with the specified amount.
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    /**
     * Debit the account with the specified amount.
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient balance");
        }
        this.balance = this.balance.subtract(amount);
    }
}
