package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    private UUID id;
    private String referenceId;
    private UUID accountId;
    private TransactionPartyResponse sender;
    private TransactionPartyResponse recipient;
    private BigDecimal amount;
    private String currency;
    private BigDecimal fee;
    private BigDecimal netAmount;
    private TransactionStatus status;
    private TransactionType type;
    private String description;
    private String failureReason;
    private String idempotencyKey;
    private Map<String, Object> metadata;
    private OffsetDateTime initiatedAt;
    private OffsetDateTime processedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime failedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionPartyResponse {
        private UUID id;
        private String name;
        private String accountNumber;
        private String email;
        private String phone;
    }
}
