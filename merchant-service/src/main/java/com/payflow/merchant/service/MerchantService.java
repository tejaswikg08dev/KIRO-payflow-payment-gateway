package com.payflow.merchant.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.merchant.model.ApiKey;
import com.payflow.merchant.model.Merchant;
import com.payflow.merchant.repository.ApiKeyRepository;
import com.payflow.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public Merchant createMerchant(Merchant merchant) {
        merchant.setId(IdGenerator.merchantId());
        // Generate webhook secret for HMAC signing
        merchant.setWebhookSecret(generateRandomString(32));
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant created: {} ({})", saved.getId(), saved.getBusinessName());
        return saved;
    }

    public Merchant getMerchant(String merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
    }

    public Merchant getMerchantByUserId(String userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "userId", userId));
    }

    /**
     * Generate API key pair (public + secret).
     * Public key: pk_tst_xxxx or pk_pay_xxxx (shown anytime)
     * Secret key: sk_tst_xxxx or sk_pay_xxxx (shown ONCE, stored as hash)
     */
    @Transactional
    public ApiKeyResult generateApiKey(String merchantId, ApiKey.KeyType keyType) {
        // Verify merchant exists
        getMerchant(merchantId);

        String prefix = keyType == ApiKey.KeyType.TEST ? "test" : "live";
        String publicKey = "pk_" + prefix + "_" + generateRandomString(20);
        String secretKey = "sk_" + prefix + "_" + generateRandomString(32);
        String secretHash = sha256Hash(secretKey);

        ApiKey apiKey = ApiKey.builder()
                .id(IdGenerator.apiKeyId())
                .merchantId(merchantId)
                .keyType(keyType)
                .publicKey(publicKey)
                .secretKeyHash(secretHash)
                .keyPrefix(secretKey.substring(0, 12)) // First 12 chars for identification
                .status(ApiKey.KeyStatus.ACTIVE)
                .build();

        apiKeyRepository.save(apiKey);
        log.info("API key generated for merchant {}: type={}", merchantId, keyType);

        // Return both keys (secret is shown only this once!)
        return new ApiKeyResult(apiKey.getId(), publicKey, secretKey, keyType);
    }

    /**
     * Validate a secret key — find merchant by hashing the provided key.
     */
    public String validateSecretKey(String secretKey) {
        String hash = sha256Hash(secretKey);
        ApiKey apiKey = apiKeyRepository.findBySecretKeyHashAndStatus(hash, ApiKey.KeyStatus.ACTIVE)
                .orElse(null);
        if (apiKey == null) return null;
        return apiKey.getMerchantId();
    }

    private String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record ApiKeyResult(String keyId, String publicKey, String secretKey, ApiKey.KeyType keyType) {}
}
