package com.flowpay.notification.repository;

import com.flowpay.notification.entity.Notification;
import com.flowpay.notification.enums.NotificationStatus;
import com.flowpay.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndStatus(UUID userId, NotificationStatus status);

    List<Notification> findByUserIdAndType(UUID userId, NotificationType type);

    List<Notification> findByTransactionId(UUID transactionId);

    List<Notification> findByStatus(NotificationStatus status);
}
