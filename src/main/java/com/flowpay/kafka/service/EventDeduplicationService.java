package com.flowpay.kafka.service;

import com.flowpay.kafka.entity.ProcessedEvent;
import com.flowpay.kafka.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventDeduplicationService {

    private static final int RETENTION_DAYS = 7;

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isDuplicate(UUID eventId, String consumerGroup) {
        return processedEventRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId, String topic, String consumerGroup) {
        ProcessedEvent event = ProcessedEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .consumerGroup(consumerGroup)
                .processedAt(OffsetDateTime.now())
                .build();
        processedEventRepository.save(event);
        log.debug("Marked event as processed: eventId={}, topic={}, consumerGroup={}",
                eventId, topic, consumerGroup);
    }

    @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    @Transactional
    public void cleanupOldEvents() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = processedEventRepository.deleteByProcessedAtBefore(cutoff);
        log.info("Cleaned up {} processed events older than {} days", deleted, RETENTION_DAYS);
    }
}
