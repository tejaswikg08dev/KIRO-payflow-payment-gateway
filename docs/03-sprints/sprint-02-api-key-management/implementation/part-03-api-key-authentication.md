# Sprint 2, Part 03: API Key Authentication Filter

**Duration:** 2 hours  
**Prerequisites:** Part 02 completed  
**Goal:** Create a gateway filter that authenticates requests using API keys

---

## 1. Learning Objectives

By the end of this part, you will:
- Understand Spring Cloud Gateway filter concepts
- Create the `ApiKeyAuthFilter` for validating X-Api-Key header
- Create the `InternalController` for gateway-to-service communication
- Implement Redis caching for high-performance validation

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY AUTHENTICATION FLOW                               │
│                                                                              │
│  Merchant Server                                                            │
│       │                                                                      │
│       │ POST /v1/payments                                                   │
│       │ X-Api-Key: sk_live_EXAMPLE_KEY_DO_NOT_USE_1234567890       │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                   API GATEWAY                                    │       │
│  │                                                                  │       │
│  │  Step 1: Extract X-Api-Key header                               │       │
│  │          sk_live_EXAMPLE_KEY_DO_NOT_USE_1234567890       │       │
│  │                   │                                              │       │
│  │                   ▼                                              │       │
│  │  Step 2: Check Redis cache                                      │       │
│  │          GET apikey:{hash_of_key}                               │       │
│  │                   │                                              │       │
│  │          ┌───────┴───────┐                                      │       │
│  │          │               │                                       │       │
│  │     Cache HIT      Cache MISS                                   │       │
│  │          │               │                                       │       │
│  │          │               ▼                                       │       │
│  │          │      Step 3: Query merchant-service                  │       │
│  │          │              POST /internal/validate-api-key         │       │
│  │          │               │                                       │       │
│  │          │               ▼                                       │       │
│  │          │      Step 4: Cache result (TTL: 5 min)               │       │
│  │          │               │                                       │       │
│  │          └───────┬───────┘                                      │       │
│  │                  │                                               │       │
│  │                  ▼                                               │       │
│  │  Step 5: Add X-Merchant-Id header                               │       │
│  │          Route to downstream service                            │       │
│  │                                                                  │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Filter Order Explanation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GATEWAY FILTER CHAIN                                      │
│                                                                              │
│  Request → CorrelationId → RateLimit → ApiKeyAuth → Route → Service        │
│            (order: -2)    (order: -1)  (order: 0)                          │
│                                                                              │
│  Why this order?                                                             │
│  ───────────────                                                            │
│  1. CorrelationId (-2): First, so ALL logs have correlation ID             │
│  2. RateLimit (-1): Before auth, so attackers can't brute-force            │
│  3. ApiKeyAuth (0): After rate limit, validates the request                │
│                                                                              │
│  If order was reversed (ApiKeyAuth → RateLimit):                           │
│  ─────────────────────────────────────────────                              │
│  Attacker could flood with invalid keys → unlimited DB queries            │
│  Rate limit after auth means attacker already caused DB load              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Create ApiKeyAuthFilter

### 4.1 Create the Filter

**File:** `api-gateway/src/main/java/com/payflow/gateway/filter/ApiKeyAuthFilter.java`

```java
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
                // JWT present — let JWT filter handle it
                return chain.filter(exchange);
            }
            return unauthorized(exchange, "Missing X-Api-Key header");
        }

        // Validate API key format
        if (!isValidKeyFormat(apiKey)) {
            return unauthorized(exchange, "Invalid API key format");
        }

        // Hash the API key for lookup
        String keyHash = sha256Hash(apiKey);
        String cacheKey = CACHE_PREFIX + keyHash;

        // Check Redis cache first
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedValue -> handleCacheHit(exchange, chain, apiKey, cachedValue))
                .switchIfEmpty(validateViaService(exchange, chain, apiKey, keyHash, cacheKey));
    }

    private Mono<Void> handleCacheHit(ServerWebExchange exchange, GatewayFilterChain chain,
                                       String apiKey, String cachedValue) {
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
        return webClient.post()
                .uri("/internal/validate-api-key")
                .bodyValue(new ValidateKeyRequest(apiKey))
                .retrieve()
                .bodyToMono(ValidateKeyResponse.class)
                .flatMap(response -> {
                    if (response.valid()) {
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
```

---

## 5. Create InternalController

### 5.1 Add Internal Endpoint

**File:** `merchant-service/src/main/java/com/payflow/merchant/controller/InternalController.java`

```java
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
        String merchantId = merchantService.validateSecretKey(request.apiKey());
        
        if (merchantId != null) {
            String keyType = request.apiKey().startsWith("sk_test_") ? "TEST" : "LIVE";
            return new ValidateKeyResponse(true, merchantId, keyType);
        }
        
        return new ValidateKeyResponse(false, null, null);
    }

    public record ValidateKeyRequest(String apiKey) {}
    public record ValidateKeyResponse(boolean valid, String merchantId, String keyType) {}
}
```

---

## 6. Update Gateway Application

### 6.1 Add WebClient Bean

**File:** `api-gateway/src/main/java/com/payflow/gateway/ApiGatewayApplication.java`

```java
package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * WebClient bean for making HTTP calls to internal services.
     * Used by ApiKeyAuthFilter to validate API keys against merchant-service.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

---

## 7. Testing

### 7.1 Start Services

```powershell
# Terminal 1: Infrastructure
docker compose -f docker-compose-infra.yml up -d

# Terminal 2: Service Registry
cd service-registry && mvn spring-boot:run

# Terminal 3: Merchant Service
cd merchant-service && mvn spring-boot:run

# Terminal 4: API Gateway
cd api-gateway && mvn spring-boot:run
```

### 7.2 Test Authentication Flow

```powershell
# 1. Generate an API key
curl -X POST "http://localhost:8082/v1/merchants/merch_xxxxx/api-keys?keyType=TEST"
# Save the secret_key from response

# 2. Test with valid key (via gateway)
curl http://localhost:8080/v1/merchants/merch_xxxxx `
  -H "X-Api-Key: sk_test_YOUR_SECRET_KEY_HERE"
# Expected: 200 OK with merchant data

# 3. Test with invalid key
curl http://localhost:8080/v1/merchants/merch_xxxxx `
  -H "X-Api-Key: sk_test_invalid"
# Expected: 401 Unauthorized

# 4. Test without key
curl http://localhost:8080/v1/merchants/merch_xxxxx
# Expected: 401 Unauthorized
```

---

## 8. File Structure After This Part

```
api-gateway/src/main/java/com/payflow/gateway/
├── ApiGatewayApplication.java         # + WebClient bean
└── filter/
    ├── CorrelationIdFilter.java       # From Sprint 1 (order: -2)
    ├── RateLimitFilter.java           # From Sprint 1 (order: -1)
    └── ApiKeyAuthFilter.java          # NEW (order: 0)

merchant-service/src/main/java/com/payflow/merchant/
├── controller/
│   ├── MerchantController.java
│   └── InternalController.java        # NEW
└── ...
```

---

## 9. Common Issues and Solutions

### Issue: "Connection refused" to merchant-service

**Cause:** WebClient hardcoded to localhost:8082
**Solution for production:** Use Eureka service discovery:

```java
// Change in ApiKeyAuthFilter constructor
this.webClient = webClientBuilder.baseUrl("lb://MERCHANT-SERVICE").build();
```

### Issue: Cache not working

**Cause:** Redis not running
**Solution:**
```powershell
docker compose -f docker-compose-infra.yml up redis -d
```

### Issue: 401 on valid key

**Cause:** SHA-256 hash mismatch
**Solution:** Ensure both use same:
- Charset (UTF-8)
- Algorithm (SHA-256)
- Hex encoding (lowercase)

---

## 10. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Filter order** | CorrelationId (-2) → RateLimit (-1) → ApiKeyAuth (0) |
| **Public paths** | /v1/auth/, /actuator/, /swagger-ui skip auth |
| **Cache TTL** | 5 minutes balance of performance/security |
| **X-Merchant-Id** | Added by gateway, trusted by downstream services |
| **Internal endpoint** | /internal/ not exposed through gateway |

---

## 11. Next Steps

**Continue to:** [part-04-webhook-configuration.md](./part-04-webhook-configuration.md)

In the next part, you'll add webhook URL configuration endpoints.

---

**End of Sprint 2, Part 03**
