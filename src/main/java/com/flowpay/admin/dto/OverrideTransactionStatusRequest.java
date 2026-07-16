package com.flowpay.admin.dto;

import com.flowpay.common.enums.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverrideTransactionStatusRequest {

    @NotNull(message = "Status is required")
    private TransactionStatus status;

    private String reason;
}
