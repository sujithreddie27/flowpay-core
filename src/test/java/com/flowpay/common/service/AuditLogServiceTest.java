package com.flowpay.common.service;

import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.AuditAction;
import com.flowpay.common.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private UUID entityId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Log Action")
    class LogAction {

        @Test
        @DisplayName("Should create audit log with all fields")
        void shouldCreateAuditLogWithAllFields() {
            Map<String, Object> oldValue = Map.of("status", "ACTIVE");
            Map<String, Object> newValue = Map.of("status", "FROZEN");
            User user = new User();
            user.setId(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            AuditLog result = auditLogService.logAction("ACCOUNT", entityId,
                    AuditAction.STATUS_CHANGE, oldValue, newValue, userId);

            assertThat(result.getEntityType()).isEqualTo("ACCOUNT");
            assertThat(result.getEntityId()).isEqualTo(entityId);
            assertThat(result.getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
            assertThat(result.getOldValue()).isEqualTo(oldValue);
            assertThat(result.getNewValue()).isEqualTo(newValue);
            assertThat(result.getPerformedBy()).isEqualTo(user);

            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("Should create audit log without performer")
        void shouldCreateAuditLogWithoutPerformer() {
            Map<String, Object> newValue = Map.of("amount", "100.00");

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            AuditLog result = auditLogService.logAction("TRANSACTION", entityId,
                    AuditAction.CREATE, newValue);

            assertThat(result.getEntityType()).isEqualTo("TRANSACTION");
            assertThat(result.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(result.getPerformedBy()).isNull();

            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should create audit log with minimal fields")
        void shouldCreateAuditLogWithMinimalFields() {
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            AuditLog result = auditLogService.logAction("USER", entityId, AuditAction.DELETE);

            assertThat(result.getEntityType()).isEqualTo("USER");
            assertThat(result.getAction()).isEqualTo(AuditAction.DELETE);
            assertThat(result.getOldValue()).isNull();
            assertThat(result.getNewValue()).isNull();
        }
    }

    @Nested
    @DisplayName("Log Status Change")
    class LogStatusChange {

        @Test
        @DisplayName("Should log status change with old and new values")
        void shouldLogStatusChange() {
            User user = new User();
            user.setId(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            AuditLog result = auditLogService.logStatusChange("ACCOUNT", entityId,
                    "ACTIVE", "FROZEN", userId);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
            assertThat(saved.getOldValue()).containsEntry("status", "ACTIVE");
            assertThat(saved.getNewValue()).containsEntry("status", "FROZEN");
        }
    }

    @Nested
    @DisplayName("Log Login")
    class LogLogin {

        @Test
        @DisplayName("Should log login with IP and user agent")
        void shouldLogLoginWithIpAndUserAgent() {
            User user = new User();
            user.setId(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            AuditLog result = auditLogService.logLogin(userId, "192.168.1.1", "Mozilla/5.0");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEntityType()).isEqualTo("USER");
            assertThat(saved.getAction()).isEqualTo(AuditAction.LOGIN);
            assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
            assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        }
    }

    @Nested
    @DisplayName("Log Transfer")
    class LogTransfer {

        @Test
        @DisplayName("Should log transfer with transaction details")
        void shouldLogTransfer() {
            Map<String, Object> details = Map.of(
                    "amount", "500.00",
                    "currency", "USD",
                    "senderId", UUID.randomUUID().toString()
            );
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditLogService.logTransfer(entityId, details, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getEntityType()).isEqualTo("TRANSACTION");
            assertThat(saved.getAction()).isEqualTo(AuditAction.TRANSFER);
            assertThat(saved.getNewValue()).containsEntry("amount", "500.00");
        }
    }

    @Nested
    @DisplayName("Query Audit Logs")
    class QueryAuditLogs {

        @Test
        @DisplayName("Should get audit logs by entity type and ID")
        void shouldGetByEntityTypeAndId() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogRepository.findByEntityTypeAndEntityId("ACCOUNT", entityId, pageable))
                    .thenReturn(page);

            Page<AuditLog> result = auditLogService.getAuditLogs("ACCOUNT", entityId, pageable);

            assertThat(result).isNotNull();
            verify(auditLogRepository).findByEntityTypeAndEntityId("ACCOUNT", entityId, pageable);
        }

        @Test
        @DisplayName("Should get audit logs by entity type only")
        void shouldGetByEntityType() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogRepository.findByEntityType("TRANSACTION", pageable)).thenReturn(page);

            Page<AuditLog> result = auditLogService.getAuditLogs("TRANSACTION", null, pageable);

            verify(auditLogRepository).findByEntityType("TRANSACTION", pageable);
        }

        @Test
        @DisplayName("Should get audit logs by date range")
        void shouldGetByDateRange() {
            Pageable pageable = PageRequest.of(0, 10);
            OffsetDateTime start = OffsetDateTime.now().minusDays(1);
            OffsetDateTime end = OffsetDateTime.now();
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogRepository.findByDateRange(start, end, pageable)).thenReturn(page);

            Page<AuditLog> result = auditLogService.getAuditLogsByDateRange(start, end, pageable);

            assertThat(result).isNotNull();
            verify(auditLogRepository).findByDateRange(start, end, pageable);
        }

        @Test
        @DisplayName("Should get audit logs by performer")
        void shouldGetByPerformer() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogRepository.findByPerformedById(userId, pageable)).thenReturn(page);

            Page<AuditLog> result = auditLogService.getAuditLogsByPerformer(userId, pageable);

            assertThat(result).isNotNull();
            verify(auditLogRepository).findByPerformedById(userId, pageable);
        }

        @Test
        @DisplayName("Should get all audit logs when no filters")
        void shouldGetAllWhenNoFilters() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> page = new PageImpl<>(List.of());
            when(auditLogRepository.findAll(pageable)).thenReturn(page);

            Page<AuditLog> result = auditLogService.getAuditLogs(null, null, pageable);

            verify(auditLogRepository).findAll(pageable);
        }
    }
}
