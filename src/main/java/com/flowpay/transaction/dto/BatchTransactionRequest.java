package com.flowpay.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchTransactionRequest {

    @NotEmpty(message = "At least one transaction is required")
    @Size(max = 100, message = "Maximum 100 transactions per batch")
    @Valid
    private List<InitiateTransactionRequest> transactions;
}
