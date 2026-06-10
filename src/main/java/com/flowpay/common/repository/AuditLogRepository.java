package com.flowpay.common.repository;

import com.flowpay.common.entity.AuditLog;
import com.flowpay.common.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AuditLog entity with custom query methods.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs by entity type.
     */
    List<AuditLog> findByEntityType(String entityType);

    /**
     * Find audit logs by entity type with pagination.
     */
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    /**
     * Find audit logs by entity ID.
     */
    List<AuditLog> findByEntityId(UUID entityId);

    /**
     * Find audit logs by entity ID with pagination.
     */
    Page<AuditLog> findByEntityId(UUID entityId, Pageable pageable);

    /**
     * Find audit logs by entity type and entity ID.
     */
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    /**
     * Find audit logs by entity type and entity ID with pagination.
     */
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);

    /**
     * Find audit logs by action.
     */
    List<AuditLog> findByAction(AuditAction action);

    /**
     * Find audit logs by action with pagination.
     */
    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    /**
     * Find audit logs by performer (user who performed the action).
     */
    List<AuditLog> findByPerformedById(UUID performedBy);

    /**
     * Find audit logs by performer with pagination.
     */
    Page<AuditLog> findByPerformedById(UUID performedBy, Pageable pageable);

    /**
     * Find audit logs created within a date range.
     */
    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :startDate AND :endDate")
    List<AuditLog> findByDateRange(@Param("startDate") OffsetDateTime startDate,
                                   @Param("endDate") OffsetDateTime endDate);

    /**
     * Find audit logs created within a date range with pagination.
     */
    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :startDate AND :endDate")
    Page<AuditLog> findByDateRange(@Param("startDate") OffsetDateTime startDate,
                                   @Param("endDate") OffsetDateTime endDate,
                                   Pageable pageable);

    /**
     * Find audit logs by entity type and action.
     */
    List<AuditLog> findByEntityTypeAndAction(String entityType, AuditAction action);

    /**
     * Find audit logs by performer and action.
     */
    List<AuditLog> findByPerformedByIdAndAction(UUID performedBy, AuditAction action);

    /**
     * Find audit logs by IP address.
     */
    List<AuditLog> findByIpAddress(String ipAddress);

    /**
     * Find audit logs by performer within a date range.
     */
    @Query("SELECT al FROM AuditLog al WHERE al.performedBy.id = :performedBy " +
           "AND al.createdAt BETWEEN :startDate AND :endDate")
    Page<AuditLog> findByPerformedByIdAndDateRange(@Param("performedBy") UUID performedBy,
                                                    @Param("startDate") OffsetDateTime startDate,
                                                    @Param("endDate") OffsetDateTime endDate,
                                                    Pageable pageable);

    /**
     * Find audit logs for an entity within a date range.
     */
    @Query("SELECT al FROM AuditLog al WHERE al.entityType = :entityType " +
           "AND al.entityId = :entityId " +
           "AND al.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY al.createdAt DESC")
    List<AuditLog> findEntityHistory(@Param("entityType") String entityType,
                                     @Param("entityId") UUID entityId,
                                     @Param("startDate") OffsetDateTime startDate,
                                     @Param("endDate") OffsetDateTime endDate);

    /**
     * Count audit logs by action.
     */
    long countByAction(AuditAction action);

    /**
     * Count audit logs by entity type.
     */
    long countByEntityType(String entityType);

    /**
     * Count audit logs by performer.
     */
    long countByPerformedById(UUID performedBy);

    /**
     * Find recent audit logs with limit.
     */
    @Query("SELECT al FROM AuditLog al ORDER BY al.createdAt DESC")
    Page<AuditLog> findRecentAuditLogs(Pageable pageable);

    /**
     * Find audit logs for security-sensitive actions.
     */
    @Query("SELECT al FROM AuditLog al WHERE al.action IN ('LOGIN', 'LOGOUT', 'STATUS_CHANGE', 'VERIFICATION') " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findSecuritySensitiveActions(Pageable pageable);

    /**
     * Get audit statistics for a date range.
     */
    @Query("SELECT NEW map(al.action as action, COUNT(al) as count) " +
           "FROM AuditLog al WHERE al.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY al.action")
    List<Object> getAuditStatsByDateRange(@Param("startDate") OffsetDateTime startDate,
                                          @Param("endDate") OffsetDateTime endDate);

    /**
     * Delete old audit logs (data retention policy).
     */
    void deleteByCreatedAtBefore(OffsetDateTime threshold);
}
