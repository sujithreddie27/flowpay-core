package com.flowpay.notification.service;

import com.flowpay.notification.dto.NotificationRequest;

public interface EmailNotificationService {

    void sendEmail(String to, String subject, String content);

    void sendPaymentNotificationEmail(NotificationRequest request);
}
