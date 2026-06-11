package com.flowpay.payments.dto;

import com.flowpay.common.enums.PaymentMethodStatus;
import com.flowpay.common.enums.PaymentMethodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {

    private UUID id;
    private UUID userId;
    private PaymentMethodType type;
    private String provider;
    private String displayName;
    private String lastFour;
    private Short expiryMonth;
    private Short expiryYear;
    private Boolean isDefault;
    private Boolean isVerified;
    private PaymentMethodStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
