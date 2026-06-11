package com.flowpay.transaction.dto;

import com.flowpay.common.enums.AccountStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountRequest {

    private AccountStatus status;

    @DecimalMin(value = "0.01", message = "Daily limit must be greater than 0")
    private BigDecimal dailyLimit;
}
