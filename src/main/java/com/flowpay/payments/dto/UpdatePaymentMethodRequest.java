package com.flowpay.payments.dto;

import com.flowpay.common.enums.PaymentMethodStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentMethodRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    private Boolean isDefault;
    private PaymentMethodStatus status;
}
