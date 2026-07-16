package com.flowpay.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorVerifyRequest {

    @NotBlank(message = "Verification code is required")
    private String code;
}
