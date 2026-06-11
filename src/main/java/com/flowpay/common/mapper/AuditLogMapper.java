package com.flowpay.common.mapper;

import com.flowpay.common.dto.AuditLogResponse;
import com.flowpay.common.entity.AuditLog;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AuditLogMapper {

    @Mapping(source = "performedBy.id", target = "performedBy")
    AuditLogResponse toResponse(AuditLog auditLog);
}
