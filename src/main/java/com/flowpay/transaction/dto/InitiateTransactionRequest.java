package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.flowpay.common.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateTransactionRequest {

    @NotNull(message = "Sender account ID is required")
    @JsonAlias({"accountId"})
    private UUID senderAccountId;

    @JsonAlias({"recipientId"})
    private UUID receiverAccountId;

    private String recipientAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 64, message = "Idempotency key must not exceed 64 characters")
    private String idempotencyKey;

    private Map<String, Object> metadata;
}
