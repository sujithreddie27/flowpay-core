package com.flowpay.common.service;

import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.AuditAction;
import com.flowpay.common.repository.AuditLogRepository;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAction(String entityType, UUID entityId, AuditAction action,
                              Map<String, Object> oldValue, Map<String, Object> newValue,
                              UUID performedByUserId) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue);

        if (performedByUserId != null) {
            userRepository.findById(performedByUserId).ifPresent(builder::performedBy);
        }

        populateRequestContext(builder);

        AuditLog auditLog = builder.build();
        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Audit log created: entityType={}, entityId={}, action={}", entityType, entityId, action);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAction(String entityType, UUID entityId, AuditAction action,
                              Map<String, Object> newValue) {
        return logAction(entityType, entityId, action, null, newValue, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAction(String entityType, UUID entityId, AuditAction action) {
        return logAction(entityType, entityId, action, null, null, null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActionAsync(String entityType, UUID entityId, AuditAction action,
                               Map<String, Object> oldValue, Map<String, Object> newValue,
                               UUID performedByUserId) {
        try {
            logAction(entityType, entityId, action, oldValue, newValue, performedByUserId);
        } catch (Exception e) {
            log.error("Async audit log failed: entityType={}, entityId={}, action={}, error={}",
                    entityType, entityId, action, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logStatusChange(String entityType, UUID entityId,
                                    String oldStatus, String newStatus,
                                    UUID performedByUserId) {
        return logAction(entityType, entityId, AuditAction.STATUS_CHANGE,
                Map.of("status", oldStatus),
                Map.of("status", newStatus),
                performedByUserId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logLogin(UUID userId, String ipAddress, String userAgent) {
        AuditLog auditLog = AuditLog.builder()
                .entityType("USER")
                .entityId(userId)
                .action(AuditAction.LOGIN)
                .ipAddress(ipAddress)
                .userAgent(truncateUserAgent(userAgent))
                .newValue(Map.of("loginTime", OffsetDateTime.now().toString()))
                .build();

        userRepository.findById(userId).ifPresent(auditLog::setPerformedBy);

        AuditLog saved = auditLogRepository.save(auditLog);
        log.debug("Login audit logged: userId={}", userId);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logTransfer(UUID transactionId, Map<String, Object> transactionDetails,
                                UUID performedByUserId) {
        return logAction("TRANSACTION", transactionId, AuditAction.TRANSFER,
                null, transactionDetails, performedByUserId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(String entityType, UUID entityId, Pageable pageable) {
        if (entityType != null && entityId != null) {
            return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        } else if (entityType != null) {
            return auditLogRepository.findByEntityType(entityType, pageable);
        } else if (entityId != null) {
            return auditLogRepository.findByEntityId(entityId, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByDateRange(OffsetDateTime startDate,
                                                   OffsetDateTime endDate,
                                                   Pageable pageable) {
        return auditLogRepository.findByDateRange(startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByPerformer(UUID performedBy, Pageable pageable) {
        return auditLogRepository.findByPerformedById(performedBy, pageable);
    }

    private void populateRequestContext(AuditLog.AuditLogBuilder builder) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                builder.ipAddress(getClientIp(request));
                builder.userAgent(truncateUserAgent(request.getHeader("User-Agent")));
            }
        } catch (Exception e) {
            log.trace("No HTTP request context available for audit log");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}
