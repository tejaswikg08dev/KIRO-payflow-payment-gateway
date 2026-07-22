package com.payflow.webhook.dto;

import java.time.Instant;

/**
 * Represents a webhook event that was delivered (or attempted) to a merchant endpoint.
 */
public record WebhookEvent(
        String eventId,
        String eventType,
        String merchantId,
        String deliveryStatus,
        int attemptCount,
        Instant createdAt
) {}
