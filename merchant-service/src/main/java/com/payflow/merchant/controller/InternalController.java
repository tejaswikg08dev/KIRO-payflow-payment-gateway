package com.payflow.merchant.controller;

import com.payflow.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoints — Called only by API Gateway, not exposed externally.
 * 
 * In production, these should be:
 * 1. On a separate port (management port)
 * 2. Protected by network policies
 * 3. Not routed through the public gateway
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final MerchantService merchantService;

    @PostMapping("/validate-api-key")
    public ValidateKeyResponse validateApiKey(@RequestBody ValidateKeyRequest request) {
        // Use existing validation method
        String merchantId = merchantService.validateSecretKey(request.apiKey());
        
        if (merchantId != null) {
            // Determine key type from prefix
            String keyType = request.apiKey().startsWith("sk_test_") ? "TEST" : "LIVE";
            return new ValidateKeyResponse(true, merchantId, keyType);
        }
        
        return new ValidateKeyResponse(false, null, null);
    }

    public record ValidateKeyRequest(String apiKey) {}
    public record ValidateKeyResponse(boolean valid, String merchantId, String keyType) {}
}
