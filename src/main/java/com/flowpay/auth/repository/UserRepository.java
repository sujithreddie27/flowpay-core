package com.flowpay.auth.repository;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity with custom query methods.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email address.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by phone number.
     */
    Optional<User> findByPhone(String phone);

    /**
     * Check if email already exists.
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone already exists.
     */
    boolean existsByPhone(String phone);

    /**
     * Find users by status.
     */
    List<User> findByStatus(UserStatus status);

    /**
     * Find users by status with pagination.
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);

    /**
     * Find users by KYC status.
     */
    List<User> findByKycStatus(KycStatus kycStatus);

    /**
     * Find users by KYC status with pagination.
     */
    Page<User> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    /**
     * Find users by status and KYC status.
     */
    List<User> findByStatusAndKycStatus(UserStatus status, KycStatus kycStatus);

    /**
     * Find users created after a specific date.
     */
    List<User> findByCreatedAtAfter(OffsetDateTime date);

    /**
     * Find users who logged in after a specific date.
     */
    List<User> findByLastLoginAtAfter(OffsetDateTime date);

    /**
     * Find users with verified email.
     */
    List<User> findByEmailVerifiedTrue();

    /**
     * Find users with unverified email.
     */
    List<User> findByEmailVerifiedFalse();

    /**
     * Update user last login timestamp.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLoginAt(@Param("userId") UUID userId, @Param("loginTime") OffsetDateTime loginTime);

    /**
     * Update user status.
     */
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :userId")
    void updateStatus(@Param("userId") UUID userId, @Param("status") UserStatus status);

    /**
     * Update user KYC status.
     */
    @Modifying
    @Query("UPDATE User u SET u.kycStatus = :kycStatus WHERE u.id = :userId")
    void updateKycStatus(@Param("userId") UUID userId, @Param("kycStatus") KycStatus kycStatus);

    /**
     * Search users by name (case-insensitive).
     */
    @Query("SELECT u FROM User u WHERE LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> searchByName(@Param("name") String name);

    /**
     * Count users by status.
     */
    long countByStatus(UserStatus status);

    /**
     * Count users by KYC status.
     */
    long countByKycStatus(KycStatus kycStatus);

    /**
     * Find inactive users who haven't logged in for a specific period.
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :threshold OR u.lastLoginAt IS NULL")
    List<User> findInactiveUsers(@Param("threshold") OffsetDateTime threshold);
}
