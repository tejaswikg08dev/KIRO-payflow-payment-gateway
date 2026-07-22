# Hands-On Guide — Phase 3 Part 4: API Gateway (Spring Cloud Gateway)

## Goal

By the end of Part 4, you will have:
- API Gateway running on port 8080 (single entry point for ALL traffic)
- Route definitions: URL patterns → backend services
- Rate limiting filter (100 requests/minute per API key, Redis-based)
- Correlation ID filter (unique trace ID on every request)
- Understanding of WHY gateway pattern is essential for microservices
- Git commit

## Prerequisites

- Part 3 completed (Config Server running on 8888)
- Eureka running on 8761
- Redis running (docker compose — needed for rate limiting)

---

## What Is an API Gateway? (Real-World Analogy)

```
IMAGINE A HOSPITAL:

WITHOUT a reception desk:
├── Patient arrives for blood test → wanders to "Lab" (3rd floor)
├── Another patient needs X-ray → wanders to "Radiology" (2nd floor)
├── Nobody checks if patients have appointments
├── Nobody tracks who went where
├── Doctors don't know patients are coming
└── Chaos! 😱

WITH a reception desk (= API Gateway):
├── Patient arrives → reception checks appointment (authentication)
├── Reception verifies insurance (API key validation)
├── Reception directs: "Lab is on 3rd floor, Room 302" (routing)
├── Reception stamps a visitor pass (correlation ID)
├── Reception counts: "Only 100 patients per hour" (rate limiting)
├── ONE entry point → controlled, tracked, secure ✅
└── Doctor doesn't deal with reception tasks (separation of concerns)

IN OUR SYSTEM:
├── "Patients" = API requests from merchants/customers
├── "Reception" = API Gateway (port 8080)
├── "3rd floor Lab" = identity-service (port 8081)
├── "2nd floor Radiology" = payment-service (port 8083)
├── "Insurance check" = API key validation
├── "Visitor pass" = X-Correlation-Id header
└── "100/hour limit" = rate limiting (Redis counter)
```

---

## What the Gateway Does (5 Things)

```
EVERY REQUEST passes through API Gateway:

1. ROUTING: Look at URL path → forward to correct service
   /v1/auth/**       → identity-service (8081)
   /v1/merchants/**  → merchant-service (8082)
   /v1/payments/**   → payment-service (8083)
   /v1/orders/**     → payment-service (8083)
   /v1/settlements/**→ settlement-service (8085)
   /v1/webhooks/**   → webhook-service (8086)

2. RATE LIMITING: Prevent abuse (too many requests)
   Each API key gets: 100 requests per minute
   Over limit → HTTP 429 "Too Many Requests"

3. CORRELATION ID: Add unique trace ID to every request
   Header: X-Correlation-Id: req_a1b2c3d4e5f6
   Passed to ALL downstream services → search logs by this ID

4. AUTHENTICATION: Validate JWT tokens / API keys
   (Implemented at each service level for now, gateway for later)

5. SWAGGER AGGREGATION: Show ALL services' API docs in one page
   (Future enhancement: http://localhost:8080/swagger-ui.html)
```

---

## Step 4.1: Create pom.xml

**Create file:** `api-gateway/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>api-gateway</artifactId>
    <name>PayFlow API Gateway</name>
    <description>Single entry point - routing, rate limiting, correlation IDs</description>

    <dependencies>
        <!-- Spring Cloud Gateway: Non-blocking, reactive gateway -->
        <!-- Note: This uses WebFlux (reactive), NOT regular Spring MVC -->
        <!-- That's why we DON'T include spring-boot-starter-web here -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Eureka Client: Discover services by name (lb://PAYMENT-SERVICE) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Reactive Redis: For rate limiting counters -->
        <!-- Must be REACTIVE (not regular) because gateway is WebFlux-based -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Actuator: /actuator/health for ALB health checks -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**⚠️ Important:** Gateway uses WebFlux (reactive/non-blocking). Do NOT add `spring-boot-starter-web` — it conflicts with the reactive gateway. That's why Gateway has different dependencies than other services.

---

## Step 4.2: Create Main Application Class

**Create file:** `api-gateway/src/main/java/com/payflow/gateway/ApiGatewayApplication.java`

```java
package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// No @EnableEurekaClient needed (auto-detected from dependency)
// No @EnableWebFlux needed (auto-detected from gateway starter)
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

---

## Step 4.3: Create application.yml (Route Definitions)

**Create file:** `api-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 8080
  # THE single entry point for all external traffic
  # Merchants/customers only ever connect to port 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      routes:
        # ===== Identity Service (auth endpoints) =====
        - id: identity-service
          uri: lb://IDENTITY-SERVICE
          # lb:// = "load balanced" — gateway asks Eureka for IDENTITY-SERVICE address
          # If 2 instances registered → gateway alternates between them
          predicates:
            - Path=/v1/auth/**
            # ANY request to /v1/auth/register, /v1/auth/login, etc.
            # → forwarded to identity-service
          filters:
            - StripPrefix=0
            # Keep the FULL path when forwarding
            # Request: /v1/auth/register → forwarded as /v1/auth/register (unchanged)

        # ===== Merchant Service =====
        - id: merchant-service
          uri: lb://MERCHANT-SERVICE
          predicates:
            - Path=/v1/merchants/**

        # ===== Payment Service (orders) =====
        - id: payment-service-orders
          uri: lb://PAYMENT-SERVICE
          predicates:
            - Path=/v1/orders/**

        # ===== Payment Service (payments) =====
        - id: payment-service-payments
          uri: lb://PAYMENT-SERVICE
          predicates:
            - Path=/v1/payments/**

        # ===== Settlement Service =====
        - id: settlement-service
          uri: lb://SETTLEMENT-SERVICE
          predicates:
            - Path=/v1/settlements/**

        # ===== Webhook Service =====
        - id: webhook-service
          uri: lb://WEBHOOK-SERVICE
          predicates:
            - Path=/v1/webhooks/**

  # Redis for rate limiting
  data:
    redis:
      host: localhost
      port: 6379

# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# Actuator (health checks)
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
  endpoint:
    gateway:
      enabled: true
      # GET /actuator/gateway/routes → list all defined routes (debugging)
```

---

## Step 4.4: Rate Limiting Filter

**Create file:** `api-gateway/src/main/java/com/payflow/gateway/filter/RateLimitFilter.java`

```java
package com.payflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Rate Limiting: Max 100 requests per minute per API key.
 * 
 * HOW IT WORKS:
 * 1. Extract identifier (API key header, or IP address if no key)
 * 2. Redis key: "rate:{identifier}" with value = request count
 * 3. INCR the key (atomic increment)
 * 4. If count was 1 → set TTL to 60 seconds (window starts now)
 * 5. If count ≤ 100 → allow request
 * 6. If count > 100 → reject with HTTP 429
 * 7. After 60 seconds → key expires → counter resets to 0
 * 
 * WHY Redis (not in-memory counter)?
 * ├── If we scale to 3 gateway instances behind ALB,
 * │   in-memory counter per instance = 3 × 100 = 300 requests allowed!
 * ├── Redis is shared → all instances increment the SAME counter
 * └── True rate limiting regardless of how many gateway instances
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final int MAX_REQUESTS = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip rate limiting for health checks
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // Get identifier: API key if present, otherwise IP
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        String identifier = (apiKey != null) ? apiKey : getClientIp(exchange);
        String redisKey = "rate:" + identifier;

        // Atomic increment in Redis
        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request in this window → set expiry
                        return redisTemplate.expire(redisKey, WINDOW)
                                .then(chain.filter(exchange));
                    } else if (count <= MAX_REQUESTS) {
                        // Within limit → allow
                        return chain.filter(exchange);
                    } else {
                        // OVER LIMIT → reject
                        log.warn("Rate limit exceeded for: {} (count: {})", identifier, count);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        // Check X-Forwarded-For header (set by ALB)
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null) return forwarded.split(",")[0].trim();
        // Fallback to remote address
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -1; // Run BEFORE routing (high priority)
    }
}
```

---

## Step 4.5: Correlation ID Filter

**Create file:** `api-gateway/src/main/java/com/payflow/gateway/filter/CorrelationIdFilter.java`

```java
package com.payflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adds X-Correlation-Id header to EVERY request.
 * 
 * This ID travels through ALL services in the request chain:
 * Client → Gateway → Payment → Routing → Bank
 * 
 * ALL services include this ID in their log messages.
 * To debug ANY issue: search CloudWatch for the correlation ID
 * → see the ENTIRE journey of that request across all services.
 * 
 * Format: "req_" + 12 random chars = "req_a1b2c3d4e5f6"
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Check if request already has correlation ID (from upstream ALB/proxy)
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }

        // Add to request (forwarded to downstream services)
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();

        // Add to response (client can see it for debugging)
        exchange.getResponse().getHeaders().add(HEADER, correlationId);

        log.info("[{}] {} {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath());

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -2; // Run FIRST (before rate limiting, before routing)
    }
}
```

---

## Step 4.6: Build and Run

```cmd
mvn clean install -DskipTests -pl api-gateway -am
cd api-gateway
mvn spring-boot:run
```

---

## Step 4.7: Verify

### Health check:
```cmd
curl http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`

### Check routes:
```cmd
curl http://localhost:8080/actuator/gateway/routes
```
Expected: JSON list of all routes defined.

### Test routing (won't succeed without backend services, but shows gateway is working):
```cmd
curl http://localhost:8080/v1/auth/register -H "Content-Type: application/json" -d "{}"
```
Expected: 503 (Service Unavailable — because identity-service isn't running yet). Gateway IS routing correctly; the backend just isn't there.

### Check Eureka dashboard (http://localhost:8761):
You should see: `API-GATEWAY` registered.

---

## Step 4.8: Git Commit

```cmd
git add api-gateway/
git commit -m "Phase 3 Part 4: API Gateway (routing, rate limiting, correlation IDs)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `pom.xml` | Gateway + Eureka + Reactive Redis |
| `ApiGatewayApplication.java` | Main class |
| `application.yml` | 6 route definitions (URL → service mapping) |
| `filter/RateLimitFilter.java` | 100 req/min per API key (Redis counter) |
| `filter/CorrelationIdFilter.java` | Unique trace ID on every request |

---

## Interview Notes

**Q: "Why use an API Gateway?"**
> "Single entry point for all traffic. Handles cross-cutting concerns (rate limiting, auth, logging, correlation IDs) in one place instead of duplicating in every service. Clients only know one URL. We can change internal service topology without affecting clients."

**Q: "How does routing work?"**
> "YAML-based route definitions match URL patterns to Eureka service names. Gateway asks Eureka for the service's address and forwards the request. If multiple instances exist, it round-robins between them."

**Q: "How does rate limiting work?"**
> "Token bucket algorithm using Redis. Each API key gets a counter in Redis with 60-second TTL. Every request increments the counter. Over 100 → HTTP 429. Redis is shared across all gateway instances so the limit is global."

---

## Next Step

→ Continue to **Phase 3 Part 5: Docker Compose Infrastructure**
