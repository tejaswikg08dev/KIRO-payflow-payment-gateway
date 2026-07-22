package com.payflow.webhook.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Generates HMAC-SHA256 signatures for webhook payloads.
 * 
 * Algorithm:
 * 1. Create signed content: "{timestamp}.{jsonBody}"
 * 2. Compute HMAC-SHA256 using merchant's webhook_secret
 * 3. Format: "sha256={hex_signature}"
 * 4. Put in header: X-PayFlow-Signature: sha256=abc123...
 * 
 * Merchant verifies by computing same HMAC with their stored secret.
 */
@Slf4j
@Service
public class SignatureGenerator {

    /**
     * Sign a webhook payload.
     * 
     * @param payload JSON body of the webhook
     * @param timestamp Unix epoch seconds (included to prevent replay attacks)
     * @param secret Merchant's webhook_secret (32-char random string)
     * @return Signature string: "sha256=abc123def456..."
     */
    public String sign(String payload, long timestamp, String secret) {
        String signedContent = timestamp + "." + payload;
        
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to compute HMAC signature: {}", e.getMessage());
            throw new RuntimeException("Signature computation failed", e);
        }
    }

    /**
     * Verify a signature (used by merchants, shown here for reference).
     */
    public boolean verify(String payload, long timestamp, String secret, String expectedSignature) {
        String computed = sign(payload, timestamp, secret);
        return computed.equals(expectedSignature);
    }
}
