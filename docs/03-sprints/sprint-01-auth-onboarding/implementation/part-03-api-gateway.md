# Sprint 1, Part 03: API Gateway

**Duration:** 2-3 hours  
**Prerequisites:** Parts 01-02 completed, Service Registry and Config Server running

---

## 1. What We're Building

In this part, you'll build the **API Gateway** - the single entry point for all client requests.

| Component | Port | Purpose |
|-----------|------|---------|
| api-gateway | 8080 | Request routing, rate limiting, request tracking |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY ROLE                                         │
│                                                                              │
│  Without API Gateway:                 With API Gateway:                      │
│  ────────────────────                 ──────────────────                     │
│                                                                              │
│  Client needs to know                 Client only knows                      │
│  ALL service URLs:                    ONE URL:                               │
│                                                                              │
│  /auth/login → localhost:8081         /v1/auth/login                        │
│  /merchants  → localhost:8082                ↓                               │
│  /payments   → localhost:8083         API Gateway (8080)                    │
│  /webhooks   → localhost:8084                ↓                               │
│                                       Routes to correct service              │
│  ❌ Client coupled to architecture                                          │
│  ❌ No central auth check             ✅ Single entry point                 │
│  ❌ No rate limiting                  ✅ Rate limiting                      │
│  ❌ No request logging                ✅ Request/response logging           │
└─────────────────────────────────────────────────────────────────────────────┘
```

> **Note:** Authentication validation (API Key filter) is added in Sprint 02.
> In Sprint 01, the gateway handles routing, rate limiting, and correlation IDs.

---

## 2. Concepts Deep Dive

### 2.1 API Gateway Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API GATEWAY RESPONSIBILITIES                              │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                         API GATEWAY                                    │ │
│  │                                                                        │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐     │ │
│  │  │   ROUTING   │ │ RATE LIMIT  │ │   LOGGING   │ │    CORS     │     │ │
│  │  │             │ │             │ │             │ │             │     │ │
│  │  │ /auth/* →   │ │ 100 req/s   │ │ Log every   │ │ Cross-      │     │ │
│  │  │ identity    │ │ per client  │ │ request     │ │ origin      │     │ │
│  │  │             │ │             │ │             │ │ requests    │     │ │
│  │  │ /merchants  │ │ Prevent     │ │ Track       │ │ allowed     │     │ │
│  │  │ → merchant  │ │ abuse       │ │ latency     │ │             │     │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘     │ │
│  │                                                                        │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  In Sprint 01, we implement:                                                 │
│  ✅ Routing (to identity-service, merchant-service, etc.)                   │
│  ✅ Rate Limiting (100 req/min per IP or API key)                           │
│  ✅ Correlation ID (request tracking)                                       │
│  ✅ CORS                                                                    │
│                                                                              │
│  In Sprint 02, we add:                                                       │
│  ⏳ API Key Authentication (ApiKeyAuthFilter)                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Spring Cloud Gateway vs Netflix Zuul

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GATEWAY COMPARISON                                        │
│                                                                              │
│  Feature              │ Spring Cloud Gateway │ Netflix Zuul                 │
│  ─────────────────────┼──────────────────────┼────────────────────────────  │
│  Architecture         │ Non-blocking         │ Blocking (Zuul 1)            │
│  Built on             │ Spring WebFlux       │ Servlet API                  │
│  Performance          │ Higher throughput    │ Lower throughput             │
│  Reactor support      │ Native               │ Not native                   │
│  Spring integration   │ First-class          │ Legacy                       │
│  Maintenance          │ Active               │ Maintenance mode             │
│                                                                              │
│  We use Spring Cloud Gateway because:                                        │
│  ✅ Non-blocking = handles more concurrent requests                         │
│  ✅ Native Spring Boot 3 support                                            │
│  ✅ Built on Project Reactor (async/reactive)                               │
│  ✅ Actively maintained                                                     │
│  ✅ Better integration with Spring Cloud ecosystem                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Gateway Filter Chain

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GATEWAY FILTER CHAIN (Sprint 01)                          │
│                                                                              │
│  Incoming Request: POST /v1/merchants                                        │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Filter 1: CorrelationIdFilter (order: -2)                            │   │
│  │ • Generate unique ID for request tracking: X-Correlation-Id         │   │
│  │ • Add to request and response headers                               │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Filter 2: RateLimitFilter (order: -1)                                │   │
│  │ • Check: Is this IP/API key over the limit?                         │   │
│  │ • If yes → Return 429 Too Many Requests                             │   │
│  │ • If no → Continue to next filter                                   │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Route Handler                                                        │   │
│  │ • Match request path to route definition                            │   │
│  │ • /v1/merchants → lb://MERCHANT-SERVICE                             │   │
│  │ • Forward request to target service                                 │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│                            merchant-service                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Before starting, verify:

```powershell
# Terminal 1: Service Registry should be running
curl http://localhost:8761/actuator/health
# Expected: {"status":"UP"}

# Terminal 2: Config Server should be running
curl http://localhost:8888/actuator/health
# Expected: {"status":"UP"}

# Redis should be running (for rate limiting)
docker ps | findstr redis
# If not running:
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

**File: `pom.xml` (project root)**

Add api-gateway to modules:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>
    <module>api-gateway</module>  <!-- ADD THIS LINE -->
</modules>
```

---

### Step 4.2: Create Folder Structure

```powershell
# Create the api-gateway module structure
mkdir api-gateway
mkdir api-gateway\src\main\java\com\payflow\gateway
mkdir api-gateway\src\main\java\com\payflow\gateway\filter
mkdir api-gateway\src\main\resources

# Verify structure
tree api-gateway /F
```

Expected:
```
api-gateway/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── payflow/
│       │           └── gateway/
│       │               └── filter/
│       └── resources/
└── pom.xml
```

---

### Step 4.3: Create pom.xml

**File: `api-gateway/pom.xml`**

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
    <description>Single entry point - routing, rate limiting</description>

    <dependencies>
        <!-- Spring Cloud Gateway (reactive, non-blocking) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Eureka Client (discover services by name) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Redis (for rate limiting) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Actuator (health checks) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
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

**Dependencies Explained:**

| Dependency | Purpose |
|------------|---------|
| `spring-cloud-starter-gateway` | Non-blocking API Gateway with route definitions |
| `spring-cloud-starter-netflix-eureka-client` | Register with Eureka for service discovery |
| `spring-boot-starter-data-redis-reactive` | Reactive Redis for rate limiting |
| `spring-boot-starter-actuator` | Health check endpoints |

---

### Step 4.4: Create Main Application Class

**File: `api-gateway/src/main/java/com/payflow/gateway/ApiGatewayApplication.java`**

```java
package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * API Gateway — The single entry point for ALL external requests.
 * 
 * What this does:
 * 1. All external traffic comes to port 8080
 * 2. Gateway examines the URL path
 * 3. Routes the request to the correct internal service:
 *    /v1/auth/**       → identity-service (8081)
 *    /v1/merchants/**  → merchant-service (8082)
 *    /v1/orders/**     → payment-service (8083)
 *    /v1/payments/**   → payment-service (8083)
 *    /v1/settlements/**→ settlement-service (8085)
 *    /v1/webhooks/**   → webhook-service (8086)
 * 4. Also provides:
 *    - Rate limiting (100 req/min per IP or API key)
 *    - Request logging (correlation IDs)
 *    - Aggregated Swagger UI (all services in one page)
 * 
 * Client sees: http://api.payflow.com/v1/payments
 * Internally routes to: http://PAYMENT-SERVICE/v1/payments
 * Client never knows about internal services!
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * WebClient bean for making HTTP calls to internal services.
     * Used by ApiKeyAuthFilter (added in Sprint 02) to validate API keys.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

---

### Step 4.5: Create application.yml

**File: `api-gateway/src/main/resources/application.yml`**

```yaml
# API Gateway Configuration
# Port: 8080 (single entry point for all external traffic)

server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      # Route definitions: URL pattern → target service
      routes:
        # === Identity Service Routes ===
        - id: identity-service
          uri: lb://IDENTITY-SERVICE    # lb:// = load-balanced via Eureka
          predicates:
            - Path=/v1/auth/**          # All auth endpoints
          filters:
            - StripPrefix=0             # Keep full path

        # === Merchant Service Routes ===
        - id: merchant-service
          uri: lb://MERCHANT-SERVICE
          predicates:
            - Path=/v1/merchants/**
          filters:
            - StripPrefix=0

        # === Payment Service Routes (Orders) ===
        - id: payment-service-orders
          uri: lb://PAYMENT-SERVICE
          predicates:
            - Path=/v1/orders/**
          filters:
            - StripPrefix=0

        # === Payment Service Routes (Payments) ===
        - id: payment-service-payments
          uri: lb://PAYMENT-SERVICE
          predicates:
            - Path=/v1/payments/**
          filters:
            - StripPrefix=0

        # === Settlement Service Routes ===
        - id: settlement-service
          uri: lb://SETTLEMENT-SERVICE
          predicates:
            - Path=/v1/settlements/**
          filters:
            - StripPrefix=0

        # === Webhook Service Routes ===
        - id: webhook-service
          uri: lb://WEBHOOK-SERVICE
          predicates:
            - Path=/v1/webhooks/**
          filters:
            - StripPrefix=0

      # Global default filters (apply to ALL routes)
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin

  # Redis for rate limiting
  data:
    redis:
      host: localhost
      port: 6379

# Eureka (discover services by name)
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
  endpoint:
    gateway:
      enabled: true

# Logging
logging:
  level:
    org.springframework.cloud.gateway: INFO
```

**Route Configuration Explained:**

| Property | Example | Purpose |
|----------|---------|---------|
| `id` | `identity-service` | Unique route name for logging |
| `uri` | `lb://IDENTITY-SERVICE` | Target service (lb = load balanced via Eureka) |
| `predicates` | `Path=/v1/auth/**` | URL pattern to match |
| `filters` | `StripPrefix=0` | Keep full path when forwarding |

---

### Step 4.6: Create CorrelationIdFilter

**File: `api-gateway/src/main/java/com/payflow/gateway/filter/CorrelationIdFilter.java`**

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
 * Correlation ID Filter — Adds a unique trace ID to every request.
 * 
 * Purpose:
 * - Every request gets a unique X-Correlation-Id header
 * - This ID is passed to all downstream services
 * - All services include it in their logs
 * - When debugging: search logs by correlation ID → see entire request journey
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = "req_" + UUID.randomUUID().toString().substring(0, 12);
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_HEADER, correlationId)
                .build();

        String finalCorrelationId = correlationId;
        exchange.getResponse().getHeaders().add(CORRELATION_HEADER, finalCorrelationId);

        log.info("[{}] {} {}", finalCorrelationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -2; // Run FIRST (before rate limiting, before routing)
    }
}
```

---

### Step 4.7: Create RateLimitFilter

**File: `api-gateway/src/main/java/com/payflow/gateway/filter/RateLimitFilter.java`**

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
 * Rate Limiting Filter — Limits requests per API key to prevent abuse.
 * 
 * How it works:
 * 1. Extract API key from X-Api-Key header (or use IP if no key)
 * 2. Check Redis counter for this key
 * 3. If count < 100 → allow request, increment counter
 * 4. If count >= 100 → reject with 429 Too Many Requests
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/eureka")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        String identifier = (apiKey != null) ? apiKey : getClientIp(exchange);
        String redisKey = "rate:" + identifier;

        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(redisKey, WINDOW)
                                .then(chain.filter(exchange));
                    } else if (count <= MAX_REQUESTS_PER_MINUTE) {
                        return chain.filter(exchange);
                    } else {
                        log.warn("Rate limit exceeded for: {}", identifier);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return -1; // Run SECOND (after correlation ID, before routing)
    }
}
```

---

## 5. Verification

### 5.1 Build the Module

```powershell
# From project root
cd api-gateway

# Clean and build
mvn clean package -DskipTests

# Expected output:
# [INFO] BUILD SUCCESS
# [INFO] Building jar: target/api-gateway-1.0.0-SNAPSHOT.jar
```

### 5.2 Start Prerequisites

```powershell
# Terminal 1: Start Redis (for rate limiting)
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Terminal 2: Start Service Registry (from Part 01)
cd service-registry
mvn spring-boot:run

# Terminal 3: Start Config Server (from Part 02)
cd config-server
mvn spring-boot:run

# Wait for both to be UP before starting gateway
```

### 5.3 Run the API Gateway

```powershell
# Terminal 4: Start API Gateway
cd api-gateway
mvn spring-boot:run
```

**Expected console output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.5)

INFO  --- NettyWebServer : Netty started on port 8080
INFO  --- ApiGatewayApplication : Started in X.XXX seconds
```

### 5.4 Test Endpoints

```powershell
# Test 1: Health check
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Test 2: Check if gateway registered with Eureka
# Open browser: http://localhost:8761
# Should see: API-GATEWAY registered

# Test 3: Test correlation ID (check response headers)
curl -v http://localhost:8080/actuator/health
# Look for: X-Correlation-Id: req_<uuid>

# Test 4: Test rate limiting (run many times quickly)
for ($i=1; $i -le 110; $i++) { curl -s -o $null -w "%{http_code}`n" http://localhost:8080/actuator/health }
# After 100 requests: Should see 429 responses
```

---

## 6. File Structure

After completing this part, you should have:

```
api-gateway/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── payflow/
│       │           └── gateway/
│       │               ├── ApiGatewayApplication.java
│       │               └── filter/
│       │                   ├── CorrelationIdFilter.java
│       │                   └── RateLimitFilter.java
│       └── resources/
│           └── application.yml
```

> **Note:** `ApiKeyAuthFilter.java` is added in Sprint 02 (API Key Management).

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ API Gateway Pattern                                                     │
│     • Single entry point for all client requests                            │
│     • Centralizes cross-cutting concerns                                    │
│                                                                              │
│  ✅ Spring Cloud Gateway                                                    │
│     • Non-blocking, reactive gateway                                        │
│     • Route definitions with predicates and filters                        │
│     • Built on Project Reactor (Mono/Flux)                                 │
│                                                                              │
│  ✅ Global Filters                                                          │
│     • CorrelationIdFilter: Request tracking across services                │
│     • RateLimitFilter: Prevent abuse (100 req/min per client)              │
│                                                                              │
│  ✅ Service Discovery                                                       │
│     • Gateway uses lb:// to route via Eureka                               │
│     • No hardcoded service URLs                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Next Steps

- **Part 04:** Identity Service Setup — Create the authentication service
- **Part 05:** Identity Database — User tables and Flyway migrations
- **Part 06:** JWT Authentication — Token generation (in identity-service)

> **Sprint 02 Preview:** In Sprint 02, you'll add `ApiKeyAuthFilter` to the gateway
> for validating merchant API keys (X-Api-Key header).

---

## 9. Troubleshooting

| Issue | Solution |
|-------|----------|
| Gateway won't start | Check if Service Registry (8761) is running first |
| Routes not working | Verify target service is registered in Eureka |
| Redis connection refused | Start Redis: `docker run -d --name redis -p 6379:6379 redis:7-alpine` |
| Rate limit not working | Check Redis is running and accessible |
