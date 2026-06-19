package com.flowpay.transaction.dto;

import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryResponse {

    private long totalTransactions;
    private long completedTransactions;
    private long failedTransactions;
    private long pendingTransactions;
    private long cancelledTransactions;

    private BigDecimal totalAmountSent;
    private BigDecimal totalAmountReceived;
    private BigDecimal totalFees;
    private BigDecimal netFlow;

    private OffsetDateTime periodStart;
    private OffsetDateTime periodEnd;

    private Map<TransactionType, Long> transactionsByType;
    private Map<TransactionStatus, Long> transactionsByStatus;
}
