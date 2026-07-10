package com.flowpay.monitoring.alerting;

public interface AlertNotificationService {

    void sendAlert(AlertEvent alertEvent);

    void sendRecovery(AlertEvent alertEvent);

    boolean isEnabled();
}
