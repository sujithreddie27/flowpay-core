package com.flowpay.transaction.repository;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transaction entity with custom query methods.
 * Implements JpaSpecificationExecutor for dynamic filtering.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, 
                                                JpaSpecificationExecutor<Transaction> {

    /**
     * Find transaction by reference ID.
     */
    Optional<Transaction> findByReferenceId(String referenceId);

    /**
     * Find transaction by idempotency key.
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Check if transaction exists by reference ID.
     */
    boolean existsByReferenceId(String referenceId);

    /**
     * Check if transaction exists by idempotency key.
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find transactions by status.
     */
    List<Transaction> findByStatus(TransactionStatus status);

    /**
     * Find transactions by status with pagination.
     */
    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    /**
     * Find transactions by type.
     */
    List<Transaction> findByType(TransactionType type);

    /**
     * Find transactions by type with pagination.
     */
    Page<Transaction> findByType(TransactionType type, Pageable pageable);

    /**
     * Find transactions by sender ID.
     */
    List<Transaction> findBySenderId(UUID senderId);

    /**
     * Find transactions by sender ID with pagination.
     */
    Page<Transaction> findBySenderId(UUID senderId, Pageable pageable);

    /**
     * Find transactions by receiver ID.
     */
    List<Transaction> findByReceiverId(UUID receiverId);

    /**
     * Find transactions by receiver ID with pagination.
     */
    Page<Transaction> findByReceiverId(UUID receiverId, Pageable pageable);

    /**
     * Find all transactions for a user (as sender or receiver).
     */
    @Query("SELECT t FROM Transaction t WHERE t.sender.id = :userId OR t.receiver.id = :userId")
    Page<Transaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find transactions by sender account ID.
     */
    List<Transaction> findBySenderAccountId(UUID senderAccountId);

    /**
     * Find transactions by receiver account ID.
     */
    List<Transaction> findByReceiverAccountId(UUID receiverAccountId);

    /**
     * Find transactions created within a date range.
     */
    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :startDate AND :endDate")
    List<Transaction> findByDateRange(@Param("startDate") OffsetDateTime startDate, 
                                      @Param("endDate") OffsetDateTime endDate);

    /**
     * Find transactions created within a date range with pagination.
     */
    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :startDate AND :endDate")
    Page<Transaction> findByDateRange(@Param("startDate") OffsetDateTime startDate, 
                                      @Param("endDate") OffsetDateTime endDate, 
                                      Pageable pageable);

    /**
     * Find transactions by sender and status.
     */
    Page<Transaction> findBySenderIdAndStatus(UUID senderId, TransactionStatus status, Pageable pageable);

    /**
     * Find transactions by sender and date range.
     */
    @Query("SELECT t FROM Transaction t WHERE t.sender.id = :senderId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Page<Transaction> findBySenderIdAndDateRange(@Param("senderId") UUID senderId,
                                                  @Param("startDate") OffsetDateTime startDate,
                                                  @Param("endDate") OffsetDateTime endDate,
                                                  Pageable pageable);

    /**
     * Find pending transactions older than specified time.
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' AND t.createdAt < :threshold")
    List<Transaction> findStalePendingTransactions(@Param("threshold") OffsetDateTime threshold);

    /**
     * Find failed transactions that can be retried.
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = 'FAILED' AND t.retryCount < 3")
    List<Transaction> findRetryableTransactions();

    /**
     * Count transactions by status.
     */
    long countByStatus(TransactionStatus status);

    /**
     * Count transactions by sender ID.
     */
    long countBySenderId(UUID senderId);

    /**
     * Count transactions for user in a date range.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sender.id = :userId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    long countByUserIdAndDateRange(@Param("userId") UUID userId,
                                   @Param("startDate") OffsetDateTime startDate,
                                   @Param("endDate") OffsetDateTime endDate);

    /**
     * Calculate total transaction amount for a user in a date range (COMPLETED only).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.sender.id = :userId AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalAmountBySenderIdAndDateRange(@Param("userId") UUID userId,
                                                     @Param("startDate") OffsetDateTime startDate,
                                                     @Param("endDate") OffsetDateTime endDate);

    /**
     * Calculate total committed + in-flight transaction amount for daily limit checks.
     * Includes PENDING, PROCESSING, and COMPLETED to prevent limit bypass via concurrent requests.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.sender.id = :userId " +
           "AND t.status IN ('PENDING', 'PROCESSING', 'COMPLETED') " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalCommittedAmountBySenderIdAndDateRange(@Param("userId") UUID userId,
                                                             @Param("startDate") OffsetDateTime startDate,
                                                             @Param("endDate") OffsetDateTime endDate);

    /**
     * Calculate total received amount for a user in a date range.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.receiver.id = :userId AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalAmountByReceiverIdAndDateRange(@Param("userId") UUID userId,
                                                       @Param("startDate") OffsetDateTime startDate,
                                                       @Param("endDate") OffsetDateTime endDate);

    /**
     * Update transaction status.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :status, t.processedAt = :processedAt " +
           "WHERE t.id = :transactionId")
    void updateStatus(@Param("transactionId") UUID transactionId, 
                      @Param("status") TransactionStatus status,
                      @Param("processedAt") OffsetDateTime processedAt);

    /**
     * Increment retry count.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.retryCount = t.retryCount + 1 WHERE t.id = :transactionId")
    void incrementRetryCount(@Param("transactionId") UUID transactionId);

    /**
     * Get transaction statistics for a date range.
     */
    @Query("SELECT NEW map(COUNT(t) as totalCount, " +
           "SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedCount, " +
           "SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END) as failedCount, " +
           "SUM(t.amount) as totalAmount) " +
           "FROM Transaction t WHERE t.createdAt BETWEEN :startDate AND :endDate")
    Object getTransactionStats(@Param("startDate") OffsetDateTime startDate,
                               @Param("endDate") OffsetDateTime endDate);

    /**
     * Find transactions with amount greater than specified value.
     */
    @Query("SELECT t FROM Transaction t WHERE t.amount > :amount")
    List<Transaction> findHighValueTransactions(@Param("amount") BigDecimal amount);

    /**
     * Find duplicate transactions (same sender, receiver, amount within time window).
     */
    @Query("SELECT t FROM Transaction t WHERE t.sender.id = :senderId " +
           "AND t.receiver.id = :receiverId AND t.amount = :amount " +
           "AND t.createdAt BETWEEN :startTime AND :endTime")
    List<Transaction> findPotentialDuplicates(@Param("senderId") UUID senderId,
                                              @Param("receiverId") UUID receiverId,
                                              @Param("amount") BigDecimal amount,
                                              @Param("startTime") OffsetDateTime startTime,
                                              @Param("endTime") OffsetDateTime endTime);
}
