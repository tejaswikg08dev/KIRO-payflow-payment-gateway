package com.payflow.notification.dto;

import java.util.Map;

/**
 * Request to send a notification (email or SMS) to a recipient.
 */
public record NotificationRequest(
        String type,
        String recipient,
        String template,
        Map<String, Object> data
) {}
