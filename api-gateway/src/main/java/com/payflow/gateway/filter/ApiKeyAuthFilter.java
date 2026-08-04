package com.payflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * API Key Authentication Filter
 * 
 * Validates API keys passed in the X-Api-Key header.
 * If valid, adds X-Merchant-Id header for downstream services.
 * Uses Redis cache for performance (TTL: 5 minutes).
 * 
 * Authentication Flow:
 * 1. Check for X-Api-Key header
 * 2. If no header and route requires auth → 401
 * 3. If header present → hash and validate
 * 4. Check Redis cache first (fast path)
 * 5. On cache miss → call merchant-service to validate
 * 6. Cache result for 5 minutes
 * 7. Add X-Merchant-Id header on success
 */
@Slf4j
@Component
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";
    private static final String CACHE_PREFIX = "apikey:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // Paths that don't require API key authentication
    private static final List<String> PUBLIC_PATHS = List.of(
            "/v1/auth/",           // Login, register (use JWT instead)
            "/actuator/",          // Health checks
            "/swagger-ui",         // API docs
            "/v3/api-docs",        // OpenAPI spec
            "/eureka"              // Service registry
    );

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;

    public ApiKeyAuthFilter(ReactiveRedisTemplate<String, String> redisTemplate,
                            WebClient.Builder webClientBuilder) {
        this.redisTemplate = redisTemplate;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8082").build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Get API key from header
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        // If no API key, check if JWT is present (allow JWT auth as fallback)
        if (apiKey == null || apiKey.isEmpty()) {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // JWT present — let JWT filter handle it (skip API key validation)
                return chain.filter(exchange);
            }
            // No API key and no JWT → 401
            return unauthorized(exchange, "Missing X-Api-Key header");
        }

        // Validate API key format (must start with sk_test_ or sk_live_)
        if (!isValidKeyFormat(apiKey)) {
            return unauthorized(exchange, "Invalid API key format");
        }

        // Hash the API key for lookup
        String keyHash = sha256Hash(apiKey);
        String cacheKey = CACHE_PREFIX + keyHash;

        // Check Redis cache first
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedValue -> {
                    // Cache HIT — parse and validate
                    return handleCacheHit(exchange, chain, apiKey, cachedValue);
                })
                .switchIfEmpty(
                    // Cache MISS — validate via merchant-service
                    validateViaService(exchange, chain, apiKey, keyHash, cacheKey)
                );
    }

    private Mono<Void> handleCacheHit(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String apiKey, String cachedValue) {
        // Value format: merchantId:keyType:status
        String[] parts = cachedValue.split(":");
        if (parts.length != 3) {
            log.warn("Invalid cache value format: {}", cachedValue);
            return unauthorized(exchange, "Invalid API key");
        }

        String merchantId = parts[0];
        String status = parts[2];

        if (!"ACTIVE".equals(status)) {
            log.warn("API key is not active: {}", maskKey(apiKey));
            return unauthorized(exchange, "API key has been revoked");
        }

        log.debug("API key validated (cache hit) for merchant: {}", merchantId);
        return continueWithMerchantId(exchange, chain, merchantId);
    }

    private Mono<Void> validateViaService(ServerWebExchange exchange, GatewayFilterChain chain,
                                          String apiKey, String keyHash, String cacheKey) {
        // Call merchant-service to validate the key
        return webClient.post()
                .uri("/internal/validate-api-key")
                .bodyValue(new ValidateKeyRequest(apiKey))
                .retrieve()
                .bodyToMono(ValidateKeyResponse.class)
                .flatMap(response -> {
                    if (response.valid()) {
                        // Cache the result
                        String cacheValue = response.merchantId() + ":" + 
                                           response.keyType() + ":ACTIVE";
                        return redisTemplate.opsForValue()
                                .set(cacheKey, cacheValue, CACHE_TTL)
                                .then(continueWithMerchantId(exchange, chain, response.merchantId()));
                    } else {
                        return unauthorized(exchange, "Invalid API key");
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error validating API key: {}", e.getMessage());
                    return unauthorized(exchange, "API key validation failed");
                });
    }

    private Mono<Void> continueWithMerchantId(ServerWebExchange exchange, 
                                               GatewayFilterChain chain, 
                                               String merchantId) {
        // Add X-Merchant-Id header for downstream services
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(MERCHANT_ID_HEADER, merchantId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        log.warn("API key authentication failed: {}", message);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("WWW-Authenticate", "ApiKey");
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory()
                        .wrap(("{\"error\":\"" + message + "\"}").getBytes()))
        );
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isValidKeyFormat(String apiKey) {
        return apiKey.startsWith("sk_test_") || apiKey.startsWith("sk_live_");
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

    private String maskKey(String apiKey) {
        if (apiKey.length() > 16) {
            return apiKey.substring(0, 12) + "..." + apiKey.substring(apiKey.length() - 4);
        }
        return "***";
    }

    @Override
    public int getOrder() {
        return 0; // After rate limit (-1), before routing
    }

    // DTOs for WebClient
    private record ValidateKeyRequest(String apiKey) {}
    private record ValidateKeyResponse(boolean valid, String merchantId, String keyType) {}
}
