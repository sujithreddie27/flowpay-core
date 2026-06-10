package com.flowpay.auth.entity;

import com.flowpay.common.entity.BaseEntity;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserRole;
import com.flowpay.common.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * User entity representing a platform user.
 * Stores user credentials, profile information, and verification status.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_status", columnList = "status"),
        @Index(name = "idx_users_kyc_status", columnList = "kyc_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Size(max = 20)
    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /**
     * Get full name of the user.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Check if user is active and can perform transactions.
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Check if user has completed KYC verification.
     */
    public boolean isKycVerified() {
        return kycStatus == KycStatus.VERIFIED;
    }
}
