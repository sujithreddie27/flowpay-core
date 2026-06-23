package com.flowpay.kafka.consumer;

import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.AuditAction;
import com.flowpay.common.enums.PaymentEventType;
import com.flowpay.common.repository.AuditLogRepository;
import com.flowpay.kafka.dto.PaymentEvent;
import com.flowpay.kafka.service.EventDeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventConsumer {

    private static final String CONSUMER_GROUP = "audit-group";
    private static final String ENTITY_TYPE_TRANSACTION = "TRANSACTION";

    private final AuditLogRepository auditLogRepository;
    private final EventDeduplicationService deduplicationService;

    @KafkaListener(
            topics = "${flowpay.kafka.topics.audit-events}",
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleAuditEvent(ConsumerRecord<String, PaymentEvent> record) {
        PaymentEvent event = record.value();
        log.info("Audit consumer received event: eventId={}, transactionId={}, eventType={}",
                event.getEventId(), event.getTransactionId(), event.getEventType());

        if (deduplicationService.isDuplicate(event.getEventId(), CONSUMER_GROUP)) {
            log.warn("Duplicate audit event, skipping: eventId={}", event.getEventId());
            return;
        }

        try {
            createAuditLog(event);
            deduplicationService.markProcessed(event.getEventId(), record.topic(), CONSUMER_GROUP);
        } catch (Exception e) {
            log.error("Failed to create audit log: eventId={}, transactionId={}, error={}",
                    event.getEventId(), event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }

    private void createAuditLog(PaymentEvent event) {
        AuditAction action = mapEventTypeToAuditAction(event.getEventType());

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("eventId", event.getEventId().toString());
        newValue.put("transactionId", event.getTransactionId().toString());
        newValue.put("referenceId", event.getReferenceId());
        newValue.put("eventType", event.getEventType().name());
        newValue.put("status", event.getTransactionStatus().name());
        newValue.put("type", event.getTransactionType().name());
        newValue.put("amount", event.getAmount());
        newValue.put("currency", event.getCurrency());
        newValue.put("senderId", event.getSenderId().toString());
        newValue.put("receiverId", event.getReceiverId().toString());
        newValue.put("timestamp", event.getTimestamp().toString());

        if (event.getFee() != null) {
            newValue.put("fee", event.getFee());
        }
        if (event.getFailureReason() != null) {
            newValue.put("failureReason", event.getFailureReason());
        }

        AuditLog auditLog = AuditLog.builder()
                .entityType(ENTITY_TYPE_TRANSACTION)
                .entityId(event.getTransactionId())
                .action(action)
                .newValue(newValue)
                .build();

        auditLogRepository.save(auditLog);
        log.debug("Audit log created: transactionId={}, action={}", event.getTransactionId(), action);
    }

    private AuditAction mapEventTypeToAuditAction(PaymentEventType eventType) {
        return switch (eventType) {
            case PAYMENT_INITIATED -> AuditAction.CREATE;
            case PAYMENT_PROCESSING -> AuditAction.STATUS_CHANGE;
            case PAYMENT_COMPLETED -> AuditAction.TRANSFER;
            case PAYMENT_FAILED -> AuditAction.STATUS_CHANGE;
            case PAYMENT_CANCELLED -> AuditAction.STATUS_CHANGE;
            case PAYMENT_REVERSED -> AuditAction.STATUS_CHANGE;
            case PAYMENT_RETRY -> AuditAction.UPDATE;
        };
    }
}
