package com.flowpay.common.dto;

import com.flowpay.common.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private AuditAction action;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private UUID performedBy;
    private String ipAddress;
    private OffsetDateTime createdAt;
}
