package com.flowpay.transaction.repository;

import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.transaction.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Account entity with custom query methods.
 * Includes optimistic locking support for concurrent balance updates.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Find account by account number.
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Find all accounts for a specific user.
     */
    List<Account> findByUserId(UUID userId);

    /**
     * Find accounts by user ID with pagination.
     */
    Page<Account> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find accounts by status.
     */
    List<Account> findByStatus(AccountStatus status);

    /**
     * Find accounts by status with pagination.
     */
    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    /**
     * Find accounts by user ID and status.
     */
    List<Account> findByUserIdAndStatus(UUID userId, AccountStatus status);

    /**
     * Find accounts by account type.
     */
    List<Account> findByAccountType(AccountType accountType);

    /**
     * Find active accounts for a user.
     */
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.status = 'ACTIVE'")
    List<Account> findActiveAccountsByUserId(@Param("userId") UUID userId);

    /**
     * Find account by user ID and currency.
     */
    Optional<Account> findByUserIdAndCurrency(UUID userId, String currency);

    /**
     * Find accounts with balance greater than specified amount.
     */
    @Query("SELECT a FROM Account a WHERE a.balance > :amount")
    List<Account> findAccountsWithBalanceGreaterThan(@Param("amount") BigDecimal amount);

    /**
     * Find accounts with balance less than specified amount.
     */
    @Query("SELECT a FROM Account a WHERE a.balance < :amount AND a.status = 'ACTIVE'")
    List<Account> findAccountsWithBalanceLessThan(@Param("amount") BigDecimal amount);

    /**
     * Find account with pessimistic write lock (for critical operations).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :accountId")
    Optional<Account> findByIdWithLock(@Param("accountId") UUID accountId);

    /**
     * Check if account exists by account number.
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Count accounts by user ID.
     */
    long countByUserId(UUID userId);

    /**
     * Count accounts by status.
     */
    long countByStatus(AccountStatus status);

    /**
     * Get total balance across all accounts for a user.
     */
    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.user.id = :userId AND a.status = 'ACTIVE'")
    BigDecimal getTotalBalanceByUserId(@Param("userId") UUID userId);

    /**
     * Update account status.
     */
    @Modifying
    @Query("UPDATE Account a SET a.status = :status WHERE a.id = :accountId")
    void updateStatus(@Param("accountId") UUID accountId, @Param("status") AccountStatus status);

    /**
     * Update account balance (use with caution - prefer optimistic locking).
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = :balance WHERE a.id = :accountId")
    void updateBalance(@Param("accountId") UUID accountId, @Param("balance") BigDecimal balance);

    /**
     * Find accounts by currency.
     */
    List<Account> findByCurrency(String currency);

    /**
     * Get account statistics for a user.
     */
    @Query("SELECT NEW map(COUNT(a) as totalAccounts, SUM(a.balance) as totalBalance) " +
           "FROM Account a WHERE a.user.id = :userId AND a.status = 'ACTIVE'")
    Object getAccountStatsByUserId(@Param("userId") UUID userId);
}
