package com.payflow.notification.service;

import com.payflow.notification.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles sending notifications via email or SMS.
 * Currently simulates SNS/SES calls by logging the operation.
 */
@Slf4j
@Service
public class NotificationService {

    /**
     * Send an email notification.
     * In production, this would publish to AWS SES or an SMTP relay.
     */
    public void sendEmail(NotificationRequest request) {
        log.info("[EMAIL] Sending template '{}' to {} | data: {}",
                request.template(), request.recipient(), request.data());

        // Simulate SNS/SES publish
        simulateExternalCall("EMAIL", request);

        log.info("[EMAIL] Successfully sent to {}", request.recipient());
    }

    /**
     * Send an SMS notification.
     * In production, this would publish to AWS SNS or a provider like Twilio.
     */
    public void sendSms(NotificationRequest request) {
        log.info("[SMS] Sending template '{}' to {} | data: {}",
                request.template(), request.recipient(), request.data());

        // Simulate SNS publish
        simulateExternalCall("SMS", request);

        log.info("[SMS] Successfully sent to {}", request.recipient());
    }

    private void simulateExternalCall(String channel, NotificationRequest request) {
        // Simulate network latency
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Notification send interrupted for {}", request.recipient());
        }
    }
}
