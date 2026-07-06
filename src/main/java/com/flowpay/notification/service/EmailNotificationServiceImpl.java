package com.flowpay.notification.service;

import com.flowpay.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${flowpay.notification.email.from:noreply@flowpay.com}")
    private String fromAddress;

    @Value("${flowpay.notification.email.enabled:true}")
    private boolean emailEnabled;

    @Override
    @Async("notificationExecutor")
    @Retryable(retryFor = Exception.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public void sendEmail(String to, String subject, String content) {
        if (!emailEnabled) {
            log.debug("Email notifications disabled, skipping email to: {}", to);
            return;
        }

        log.info("Sending email notification: to={}, subject={}", to, subject);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);

        mailSender.send(message);
        log.info("Email sent successfully: to={}", to);
    }

    @Override
    @Async("notificationExecutor")
    public void sendPaymentNotificationEmail(NotificationRequest request) {
        if (!emailEnabled || request.getRecipientEmail() == null) {
            log.debug("Skipping payment notification email: enabled={}, recipient={}",
                    emailEnabled, request.getRecipientEmail());
            return;
        }

        sendEmail(request.getRecipientEmail(), request.getSubject(), request.getContent());
    }
}
