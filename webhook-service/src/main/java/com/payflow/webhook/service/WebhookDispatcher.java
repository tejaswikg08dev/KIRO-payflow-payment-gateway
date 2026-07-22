package com.payflow.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Dispatches webhook events to merchant's URL.
 * 
 * Steps:
 * 1. Build JSON payload
 * 2. Sign with HMAC-SHA256
 * 3. POST to merchant's webhook URL
 * 4. If 2xx → success (mark delivered)
 * 5. If fail → schedule retry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

    private final SignatureGenerator signatureGenerator;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Deliver a webhook event to a merchant.
     * 
     * @param webhookUrl Merchant's endpoint (e.g., "https://merchant.com/webhooks")
     * @param payload JSON string of the event data
     * @param webhookSecret Merchant's secret for HMAC signing
     * @return true if delivered (2xx response), false if failed
     */
    public boolean deliver(String webhookUrl, String payload, String webhookSecret) {
        long timestamp = Instant.now().getEpochSecond();
        String signature = signatureGenerator.sign(payload, timestamp, webhookSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-PayFlow-Signature", signature);
        headers.set("X-PayFlow-Timestamp", String.valueOf(timestamp));
        headers.set("User-Agent", "PayFlow-Webhook/1.0");

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook delivered to {} (status: {})", webhookUrl, response.getStatusCode());
                return true;
            } else {
                log.warn("Webhook delivery failed: {} returned {}", webhookUrl, response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.warn("Webhook delivery error to {}: {}", webhookUrl, e.getMessage());
            return false;
        }
    }
}
