package com.flowpay.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountStatementResponse {

    private UUID accountId;
    private String accountNumber;
    private String currency;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private OffsetDateTime fromDate;
    private OffsetDateTime toDate;
    private int totalTransactions;
    private List<StatementEntry> entries;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatementEntry {
        private OffsetDateTime date;
        private String referenceId;
        private String description;
        private String type;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal balance;
    }
}
