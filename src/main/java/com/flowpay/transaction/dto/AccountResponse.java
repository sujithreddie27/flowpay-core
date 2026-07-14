package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String accountNumber;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private AccountType accountType;
    private AccountStatus status;
    private BigDecimal dailyLimit;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastActivityAt;
}
