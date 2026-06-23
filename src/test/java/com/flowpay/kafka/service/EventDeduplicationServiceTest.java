package com.flowpay.kafka.service;

import com.flowpay.kafka.entity.ProcessedEvent;
import com.flowpay.kafka.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventDeduplicationServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private EventDeduplicationService eventDeduplicationService;

    @Captor
    private ArgumentCaptor<ProcessedEvent> eventCaptor;

    @Nested
    @DisplayName("isDuplicate")
    class IsDuplicate {

        @Test
        @DisplayName("Should return true when event already processed")
        void shouldReturnTrueForProcessedEvent() {
            UUID eventId = UUID.randomUUID();
            when(processedEventRepository.existsByEventIdAndConsumerGroup(eventId, "test-group"))
                    .thenReturn(true);

            boolean result = eventDeduplicationService.isDuplicate(eventId, "test-group");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when event not processed")
        void shouldReturnFalseForNewEvent() {
            UUID eventId = UUID.randomUUID();
            when(processedEventRepository.existsByEventIdAndConsumerGroup(eventId, "test-group"))
                    .thenReturn(false);

            boolean result = eventDeduplicationService.isDuplicate(eventId, "test-group");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("markProcessed")
    class MarkProcessed {

        @Test
        @DisplayName("Should save processed event with correct fields")
        void shouldSaveProcessedEvent() {
            UUID eventId = UUID.randomUUID();
            when(processedEventRepository.save(any(ProcessedEvent.class)))
                    .thenAnswer(i -> i.getArgument(0));

            eventDeduplicationService.markProcessed(eventId, "payment-completed", "test-group");

            verify(processedEventRepository).save(eventCaptor.capture());
            ProcessedEvent saved = eventCaptor.getValue();
            assertThat(saved.getEventId()).isEqualTo(eventId);
            assertThat(saved.getTopic()).isEqualTo("payment-completed");
            assertThat(saved.getConsumerGroup()).isEqualTo("test-group");
            assertThat(saved.getProcessedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("cleanupOldEvents")
    class CleanupOldEvents {

        @Test
        @DisplayName("Should delete events older than retention period")
        void shouldDeleteOldEvents() {
            when(processedEventRepository.deleteByProcessedAtBefore(any(OffsetDateTime.class)))
                    .thenReturn(50);

            eventDeduplicationService.cleanupOldEvents();

            verify(processedEventRepository).deleteByProcessedAtBefore(any(OffsetDateTime.class));
        }
    }
}
