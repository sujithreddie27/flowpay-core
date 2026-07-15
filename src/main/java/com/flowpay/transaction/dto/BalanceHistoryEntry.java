package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceHistoryEntry {

    private OffsetDateTime date;
    private BigDecimal balance;
    private BigDecimal change;
    private String description;
    private String type;
    private String referenceId;
}
