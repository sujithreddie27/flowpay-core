package com.flowpay.payments.entity;

import com.flowpay.auth.entity.User;
import com.flowpay.common.entity.BaseEntity;
import com.flowpay.common.enums.PaymentMethodStatus;
import com.flowpay.common.enums.PaymentMethodType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * PaymentMethod entity representing a user's payment instrument.
 * Stores tokenized payment details for security.
 */
@Entity
@Table(name = "payment_methods", indexes = {
        @Index(name = "idx_payment_methods_user_id", columnList = "user_id"),
        @Index(name = "idx_payment_methods_type", columnList = "type"),
        @Index(name = "idx_payment_methods_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_methods_user"))
    @NotNull
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @NotNull
    private PaymentMethodType type;

    @NotBlank
    @Size(max = 50)
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Size(max = 100)
    @Column(name = "display_name", length = 100)
    private String displayName;

    @NotBlank
    @Size(max = 500)
    @Column(name = "tokenized_details", nullable = false, length = 500)
    private String tokenizedDetails;

    @Size(max = 4)
    @Column(name = "last_four", length = 4)
    private String lastFour;

    @Column(name = "expiry_month")
    private Short expiryMonth;

    @Column(name = "expiry_year")
    private Short expiryYear;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentMethodStatus status = PaymentMethodStatus.ACTIVE;

    /**
     * Check if payment method is active and can be used.
     */
    public boolean isActive() {
        return status == PaymentMethodStatus.ACTIVE;
    }

    /**
     * Check if payment method is expired (for cards).
     */
    public boolean isExpired() {
        if (type != PaymentMethodType.CARD || expiryMonth == null || expiryYear == null) {
            return false;
        }
        
        int currentYear = java.time.Year.now().getValue();
        int currentMonth = java.time.MonthDay.now().getMonthValue();
        
        return expiryYear < currentYear || 
               (expiryYear == currentYear && expiryMonth < currentMonth);
    }

    /**
     * Get masked display string for the payment method.
     */
    public String getMaskedDisplay() {
        if (lastFour != null) {
            return String.format("%s ending in %s", type.name(), lastFour);
        }
        return displayName != null ? displayName : type.name();
    }
}
