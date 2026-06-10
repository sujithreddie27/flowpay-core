package com.flowpay.payments.repository;

import com.flowpay.common.enums.PaymentMethodStatus;
import com.flowpay.common.enums.PaymentMethodType;
import com.flowpay.payments.entity.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PaymentMethod entity with custom query methods.
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    /**
     * Find all payment methods for a user.
     */
    List<PaymentMethod> findByUserId(UUID userId);

    /**
     * Find payment methods by user ID with pagination.
     */
    Page<PaymentMethod> findByUserId(UUID userId, Pageable pageable);

    /**
     * Find payment methods by user ID and status.
     */
    List<PaymentMethod> findByUserIdAndStatus(UUID userId, PaymentMethodStatus status);

    /**
     * Find payment methods by user ID and type.
     */
    List<PaymentMethod> findByUserIdAndType(UUID userId, PaymentMethodType type);

    /**
     * Find active payment methods for a user.
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.user.id = :userId AND pm.status = 'ACTIVE'")
    List<PaymentMethod> findActivePaymentMethodsByUserId(@Param("userId") UUID userId);

    /**
     * Find default payment method for a user.
     */
    Optional<PaymentMethod> findByUserIdAndIsDefaultTrue(UUID userId);

    /**
     * Find verified payment methods for a user.
     */
    List<PaymentMethod> findByUserIdAndIsVerifiedTrue(UUID userId);

    /**
     * Find payment methods by type.
     */
    List<PaymentMethod> findByType(PaymentMethodType type);

    /**
     * Find payment methods by status.
     */
    List<PaymentMethod> findByStatus(PaymentMethodStatus status);

    /**
     * Find payment methods by provider.
     */
    List<PaymentMethod> findByProvider(String provider);

    /**
     * Count payment methods by user ID.
     */
    long countByUserId(UUID userId);

    /**
     * Count active payment methods by user ID.
     */
    @Query("SELECT COUNT(pm) FROM PaymentMethod pm WHERE pm.user.id = :userId AND pm.status = 'ACTIVE'")
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Check if user has a default payment method.
     */
    boolean existsByUserIdAndIsDefaultTrue(UUID userId);

    /**
     * Update payment method status.
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.status = :status WHERE pm.id = :paymentMethodId")
    void updateStatus(@Param("paymentMethodId") UUID paymentMethodId, 
                      @Param("status") PaymentMethodStatus status);

    /**
     * Set payment method as default and unset others.
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isDefault = false WHERE pm.user.id = :userId")
    void unsetDefaultForUser(@Param("userId") UUID userId);

    /**
     * Mark payment method as verified.
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isVerified = true WHERE pm.id = :paymentMethodId")
    void markAsVerified(@Param("paymentMethodId") UUID paymentMethodId);

    /**
     * Find expired card payment methods.
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.type = 'CARD' " +
           "AND pm.status = 'ACTIVE' " +
           "AND ((pm.expiryYear < :currentYear) OR " +
           "(pm.expiryYear = :currentYear AND pm.expiryMonth < :currentMonth))")
    List<PaymentMethod> findExpiredCards(@Param("currentYear") int currentYear, 
                                         @Param("currentMonth") int currentMonth);

    /**
     * Find cards expiring soon (within specified months).
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.type = 'CARD' " +
           "AND pm.status = 'ACTIVE' " +
           "AND pm.expiryYear = :targetYear AND pm.expiryMonth = :targetMonth")
    List<PaymentMethod> findCardsExpiringSoon(@Param("targetYear") int targetYear, 
                                               @Param("targetMonth") int targetMonth);

    /**
     * Delete payment methods by user ID (soft delete by updating status).
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.status = 'REVOKED' WHERE pm.user.id = :userId")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Count payment methods by type for a user.
     */
    long countByUserIdAndType(UUID userId, PaymentMethodType type);
}
