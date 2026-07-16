package com.flowpay.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRetryRequest {

    @NotEmpty(message = "Transaction IDs are required")
    private List<UUID> transactionIds;
}
