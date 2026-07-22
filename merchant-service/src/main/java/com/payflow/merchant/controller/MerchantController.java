package com.payflow.merchant.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.merchant.model.ApiKey;
import com.payflow.merchant.model.Merchant;
import com.payflow.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchants", description = "Merchant onboarding and management")
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    @Operation(summary = "Register a new merchant")
    public ResponseEntity<ApiResponse<Merchant>> createMerchant(@RequestBody Merchant merchant) {
        Merchant created = merchantService.createMerchant(merchant);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant by ID")
    public ResponseEntity<ApiResponse<Merchant>> getMerchant(@PathVariable String merchantId) {
        Merchant merchant = merchantService.getMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(merchant));
    }

    @PostMapping("/{merchantId}/api-keys")
    @Operation(summary = "Generate API key pair (public + secret)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateApiKey(
            @PathVariable String merchantId,
            @RequestParam(defaultValue = "TEST") String keyType) {

        ApiKey.KeyType type = ApiKey.KeyType.valueOf(keyType.toUpperCase());
        MerchantService.ApiKeyResult result = merchantService.generateApiKey(merchantId, type);

        Map<String, Object> response = Map.of(
                "key_id", result.keyId(),
                "key_type", result.keyType().name(),
                "public_key", result.publicKey(),
                "secret_key", result.secretKey(),
                "note", "Save the secret_key now. It will NOT be shown again."
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
