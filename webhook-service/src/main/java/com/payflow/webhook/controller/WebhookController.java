package com.payflow.webhook.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.webhook.dto.WebhookEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks")
@Tag(name = "Webhooks", description = "Webhook event management and retry operations")
public class WebhookController {

    /**
     * List all webhook events for the current merchant.
     */
    @GetMapping("/events")
    @Operation(summary = "List webhook events", description = "Returns all webhook delivery events for the authenticated merchant")
    public ResponseEntity<ApiResponse<List<WebhookEvent>>> getWebhookEvents() {
        log.info("Fetching webhook events");

        // Placeholder: in real implementation, fetch from DB
        List<WebhookEvent> events = List.of(
                new WebhookEvent("evt_001", "payment.captured", "merchant_01", "delivered", 1, Instant.now()),
                new WebhookEvent("evt_002", "payment.failed", "merchant_01", "failed", 3, Instant.now())
        );

        return ResponseEntity.ok(ApiResponse.success(events));
    }

    /**
     * Retry delivery of a specific webhook event.
     */
    @PostMapping("/events/{id}/retry")
    @Operation(summary = "Retry webhook delivery", description = "Re-attempts delivery of a failed webhook event")
    public ResponseEntity<ApiResponse<WebhookEvent>> retryWebhookEvent(@PathVariable String id) {
        log.info("Retrying webhook event: {}", id);

        // Placeholder: in real implementation, queue the retry
        WebhookEvent retried = new WebhookEvent(
                id, "payment.captured", "merchant_01", "pending", 4, Instant.now()
        );

        return ResponseEntity.accepted().body(ApiResponse.success(retried));
    }
}
