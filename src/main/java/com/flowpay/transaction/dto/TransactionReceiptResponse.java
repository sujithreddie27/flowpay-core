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
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionReceiptResponse {

    private UUID transactionId;
    private String referenceId;
    private TransactionStatus status;
    private TransactionType type;

    private BigDecimal amount;
    private String currency;
    private BigDecimal fee;
    private BigDecimal totalAmount;

    private String senderName;
    private UUID senderAccountId;
    private String senderAccountNumber;

    private String receiverName;
    private UUID receiverAccountId;
    private String receiverAccountNumber;

    private String description;
    private String failureReason;

    private OffsetDateTime initiatedAt;
    private OffsetDateTime completedAt;

    private Map<String, Object> metadata;
}
