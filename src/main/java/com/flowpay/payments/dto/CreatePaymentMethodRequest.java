package com.flowpay.payments.dto;

import com.flowpay.common.enums.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentMethodRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType type;

    @NotBlank(message = "Provider is required")
    @Size(max = 50, message = "Provider must not exceed 50 characters")
    private String provider;

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @NotBlank(message = "Tokenized details are required")
    @Size(max = 500, message = "Tokenized details must not exceed 500 characters")
    private String tokenizedDetails;

    @Size(max = 4, message = "Last four must be exactly 4 digits")
    private String lastFour;

    private Short expiryMonth;
    private Short expiryYear;
    private Boolean isDefault;
}
