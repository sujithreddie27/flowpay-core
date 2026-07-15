package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.common.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionStatusResponse {

    private UUID id;
    private String referenceId;
    private TransactionStatus status;
    private OffsetDateTime updatedAt;
}
