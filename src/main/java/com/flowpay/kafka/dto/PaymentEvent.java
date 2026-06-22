package com.flowpay.kafka.dto;

import com.flowpay.common.enums.PaymentEventType;
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
public class PaymentEvent {

    private UUID eventId;
    private UUID transactionId;
    private String referenceId;
    private PaymentEventType eventType;
    private TransactionStatus transactionStatus;
    private TransactionType transactionType;
    private UUID senderId;
    private UUID receiverId;
    private UUID senderAccountId;
    private UUID receiverAccountId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal fee;
    private String failureReason;
    private OffsetDateTime timestamp;
    private Map<String, Object> metadata;
}
