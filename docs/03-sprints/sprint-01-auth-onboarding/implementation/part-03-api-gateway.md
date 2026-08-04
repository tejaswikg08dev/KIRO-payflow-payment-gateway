# Sprint 1, Part 03: API Gateway

**Duration:** 2-3 hours  
**Prerequisites:** Parts 01-02 completed, Service Registry and Config Server running

---

## 1. What We're Building

In this part, you'll build the **API Gateway** - the single entry point for all client requests.

| Component | Port | Purpose |
|-----------|------|---------|
| api-gateway | 8080 | Request routing, authentication, rate limiting |

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
│  /auth/login → localhost:8081         /api/v1/auth/login                    │
│  /merchants  → localhost:8082                ↓                               │
│  /payments   → localhost:8083         API Gateway (8080)                    │
│  /webhooks   → localhost:8084                ↓                               │
│                                       Routes to correct service              │
│  ❌ Client coupled to architecture                                          │
│  ❌ No central auth check             ✅ Single entry point                 │
│  ❌ No rate limiting                  ✅ Central authentication             │
│  ❌ No request logging                ✅ Rate limiting                      │
│                                       ✅ Request/response logging           │
└─────────────────────────────────────────────────────────────────────────────┘
```

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
│  │  │   ROUTING   │ │    AUTH     │ │ RATE LIMIT  │ │   LOGGING   │     │ │
│  │  │             │ │             │ │             │ │             │     │ │
│  │  │ /auth/* →   │ │ Validate    │ │ 100 req/s   │ │ Log every   │     │ │
│  │  │ identity    │ │ JWT token   │ │ per client  │ │ request     │     │ │
│  │  │             │ │             │ │             │ │             │     │ │
│  │  │ /merchants  │ │ Extract     │ │ Prevent     │ │ Track       │     │ │
│  │  │ → merchant  │ │ user info   │ │ abuse       │ │ latency     │     │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘     │ │
│  │                                                                        │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐     │ │
│  │  │ LOAD BAL    │ │ CIRCUIT     │ │  CORS       │ │ TRANSFORM   │     │ │
│  │  │             │ │ BREAKER     │ │             │ │             │     │ │
│  │  │ Distribute  │ │ Fail fast   │ │ Cross-      │ │ Modify      │     │ │
│  │  │ requests    │ │ if service  │ │ origin      │ │ request/    │     │ │
│  │  │ across      │ │ is down     │ │ requests    │ │ response    │     │ │
│  │  │ instances   │ │             │ │ allowed     │ │             │     │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘     │ │
│  │                                                                        │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  In this part, we'll implement:                                              │
│  ✅ Routing                                                                 │
│  ✅ JWT Authentication                                                      │
│  ✅ Rate Limiting                                                           │
│  ✅ Correlation ID (request tracking)                                       │
│  ✅ CORS                                                                    │
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
│  BLOCKING vs NON-BLOCKING:                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Blocking (Zuul):           Non-blocking (Gateway):                 │   │
│  │                                                                      │   │
│  │  Request 1 → Thread 1       Request 1 ─┐                            │   │
│  │  Request 2 → Thread 2       Request 2 ─┼─► Single thread            │   │
│  │  Request 3 → Thread 3       Request 3 ─┘   handles many             │   │
│  │  ...                                       requests                  │   │
│  │  Request N → Thread N                                                │   │
│  │                                                                      │   │
│  │  1000 requests = 1000       1000 requests = ~10 threads             │   │
│  │  threads (expensive!)       (very efficient!)                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Gateway Filter Chain

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GATEWAY FILTER CHAIN                                      │
│                                                                              │
│  Incoming Request: POST /api/v1/merchants                                    │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Filter 1: CorrelationIdFilter                                        │   │
│  │ • Generate unique ID for request tracking: X-Correlation-ID         │   │
│  │ • Add to request headers                                             │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Filter 2: RateLimitFilter                                            │   │
│  │ • Check: Is this IP/API key over the limit?                         │   │
│  │ • If yes → Return 429 Too Many Requests                             │   │
│  │ • If no → Continue to next filter                                   │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Filter 3: JwtAuthenticationFilter                                    │   │
│  │ • Check: Is this a protected route?                                 │   │
│  │ • If protected: Validate JWT token                                  │   │
│  │   • Extract token from Authorization header                         │   │
│  │   • Verify signature with public key                                │   │
│  │   • Check expiration                                                │   │
│  │   • If invalid → Return 401 Unauthorized                            │   │
│  │   • If valid → Add user info to headers, continue                   │   │
│  │ • If public route: Skip validation, continue                        │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Route Handler                                                        │   │
│  │ • Match request path to route definition                            │   │
│  │ • /api/v1/merchants → lb://merchant-service                         │   │
│  │ • Forward request to target service                                 │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│                            merchant-service                                  │
│                                   │                                          │
│                                   ▼                                          │
│                            Response flows back                               │
│                            through filters                                   │
│                                   │                                          │
│                                   ▼                                          │
│                                Client                                        │
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

# Config Server should have api-gateway config
curl http://localhost:8888/api-gateway/default
# Expected: JSON with server.port: 8080
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
mkdir api-gateway\src\main\java\com\payflow\gateway\config
mkdir api-gateway\src\main\java\com\payflow\gateway\filter
mkdir api-gateway\src\main\java\com\payflow\gateway\util
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
│       │               ├── config/
│       │               ├── filter/
│       │               └── util/
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
    <description>Single entry point - routing, rate limiting, authentication</description>

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

### Step 4.4: Create Main Application Class

**File: `api-gateway/src/main/java/com/payflow/gateway/ApiGatewayApplication.java`**

```java
package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 *    - Rate limiting (100 req/sec per API key)
 *    - Authentication validation (check JWT/API key)
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

### Step 4.6: Create Gateway Filters

The gateway includes custom filters for request tracking and rate limiting. These are already implemented in the source code.

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
        return -1; // Run BEFORE routing (high priority)
    }
}
```
                .getPayload();
                
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if token is expired.
     * 
     * @param claims Claims from validated token
     * @return true if expired, false if still valid
     */
    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
    
    /**
     * Extract user ID from claims.
     * 
     * @param claims Claims from validated token
     * @return User ID (UUID as string)
     */
    public String getUserId(Claims claims) {
        return claims.getSubject();
    }
    
    /**
     * Extract user email from claims.
     * 
     * @param claims Claims from validated token
     * @return User email
     */
    public String getEmail(Claims claims) {
        return claims.get("email", String.class);
    }
    
    /**
     * Extract user role from claims.
     * 
     * @param claims Claims from validated token
     * @return User role (e.g., "MERCHANT", "ADMIN")
     */
    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
```



---

### Step 4.8: Create CorrelationIdFilter

**File: `api-gateway/src/main/java/com/payflow/gateway/filter/CorrelationIdFilter.java`**

```java
package com.payflow.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * CORRELATION ID FILTER
 * Generates unique ID for each request for distributed tracing
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Why Correlation IDs?
 * ────────────────────
 * In microservices, a single user request can touch multiple services:
 * 
 *   User Request → API Gateway → Identity → Merchant → Payment → Bank
 * 
 * Each service logs separately. How do you trace one request across all logs?
 * 
 * Solution: Generate a unique ID at the gateway and pass it to all services.
 * All services include this ID in their logs.
 * 
 * Search logs for: correlation-id=abc-123
 * Result: See the complete journey of that request
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    
    /**
     * Filter execution logic.
     * 
     * @param exchange Contains request and response objects
     * @param chain The filter chain to continue execution
     * @return Mono<Void> - Reactive completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        // Check if correlation ID already exists (from another gateway, etc.)
        String correlationId = exchange.getRequest().getHeaders()
            .getFirst(CORRELATION_ID_HEADER);
        
        // Generate new ID if not present
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Using existing correlation ID: {}", correlationId);
        }

        
        // Add correlation ID to request headers (for downstream services)
        final String finalCorrelationId = correlationId;
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
            .header(CORRELATION_ID_HEADER, finalCorrelationId)
            .build();
        
        // Add correlation ID to response headers (for client debugging)
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);
        
        // Continue with modified request
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    /**
     * Filter order - lower number = runs earlier.
     * 
     * This filter should run FIRST (before auth, rate limiting, etc.)
     * so all subsequent filters and services have the correlation ID.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // -2147483648 (runs first)
    }
}
```


---

### Step 4.9: Create RateLimitFilter

**File: `api-gateway/src/main/java/com/payflow/gateway/filter/RateLimitFilter.java`**

```java
package com.payflow.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;


/**
 * ═══════════════════════════════════════════════════════════════════════════
 * RATE LIMIT FILTER
 * Limits requests per client to prevent abuse
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Why Rate Limiting?
 * ──────────────────
 * Without limits, a single client could:
 * • Overwhelm the system with requests (DoS attack)
 * • Scrape all your data
 * • Run up your cloud costs
 * 
 * Rate Limiting Algorithm: Fixed Window Counter
 * ─────────────────────────────────────────────
 * 
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Time Window: 1 second                     Limit: 100 requests          │
 * │                                                                          │
 * │  |──────── Second 1 ────────|──────── Second 2 ────────|                │
 * │  | Req 1, 2, 3...99, 100 ✓ | Req 1, 2, 3...           |                │
 * │  | Req 101, 102... ❌       | Counter resets to 0      |                │
 * │  |                          |                           |                │
 * └─────────────────────────────────────────────────────────────────────────┘
 * 
 * Redis Key Pattern:
 * • Key: rate_limit:{clientIP}:{currentSecond}
 * • Value: Request count
 * • TTL: 1 second
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    
    private final ReactiveStringRedisTemplate redisTemplate;
    
    @Value("${rate-limit.requests-per-second:100}")
    private int requestsPerSecond;
    
    public RateLimitFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        // Get client identifier (IP address)
        String clientIp = getClientIp(exchange);
        
        // Build Redis key: rate_limit:{ip}:{second}
        long currentSecond = System.currentTimeMillis() / 1000;
        String key = "rate_limit:" + clientIp + ":" + currentSecond;

        
        // Increment counter and check limit
        return redisTemplate.opsForValue().increment(key)
            .flatMap(count -> {
                // Set expiry on first request of the second
                if (count == 1) {
                    return redisTemplate.expire(key, Duration.ofSeconds(1))
                        .thenReturn(count);
                }
                return Mono.just(count);
            })
            .flatMap(count -> {
                if (count > requestsPerSecond) {
                    // Over limit - return 429 Too Many Requests
                    log.warn("Rate limit exceeded for IP: {}, count: {}", clientIp, count);
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders()
                        .add("X-Rate-Limit-Retry-After", "1");
                    return exchange.getResponse().setComplete();
                }
                // Under limit - continue
                return chain.filter(exchange);
            })
            .onErrorResume(e -> {
                // Redis error - allow request (fail open)
                log.error("Rate limit check failed: {}", e.getMessage());
                return chain.filter(exchange);
            });
    }

    
    /**
     * Extract client IP from request.
     * Handles proxied requests by checking X-Forwarded-For header.
     */
    private String getClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders()
            .getFirst("X-Forwarded-For");
        
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For format: client, proxy1, proxy2
            return forwardedFor.split(",")[0].trim();
        }
        
        // Fallback to direct connection IP
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? 
            remoteAddress.getAddress().getHostAddress() : "unknown";
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;  // After CorrelationIdFilter
    }
}
```


---

### Step 4.10: Create JwtAuthenticationFilter

**File: `api-gateway/src/main/java/com/payflow/gateway/filter/JwtAuthenticationFilter.java`**

```java
package com.payflow.gateway.filter;

import com.payflow.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;


/**
 * ═══════════════════════════════════════════════════════════════════════════
 * JWT AUTHENTICATION FILTER
 * Validates JWT tokens for protected routes
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Authentication Flow:
 * ───────────────────
 * 
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ Request: POST /api/v1/merchants                                         │
 * │ Header: Authorization: Bearer eyJhbGciOiJSUzI1NiIs...                  │
 * │                                                                          │
 * │ 1. Is /api/v1/merchants a protected route?                              │
 * │    └── YES (not in PUBLIC_PATHS)                                        │
 * │                                                                          │
 * │ 2. Extract token from Authorization header                              │
 * │    └── "Bearer " prefix removed                                         │
 * │    └── Token: eyJhbGciOiJSUzI1NiIs...                                  │
 * │                                                                          │
 * │ 3. Validate token with JwtUtil                                          │
 * │    └── Verify signature with public key                                 │
 * │    └── Check expiration                                                 │
 * │    └── Extract claims (userId, email, role)                             │
 * │                                                                          │
 * │ 4. Add user info to request headers (for downstream services)           │
 * │    └── X-User-ID: abc-123                                               │
 * │    └── X-User-Email: merchant@example.com                               │
 * │    └── X-User-Role: MERCHANT                                            │
 * │                                                                          │
 * │ 5. Continue to next filter → Route handler → Backend service            │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    // Routes that don't require authentication
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/actuator/health",
        "/actuator/info"
    );
    
    private final JwtUtil jwtUtil;
    
    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        String path = exchange.getRequest().getPath().value();
        
        // Skip auth for public paths
        if (isPublicPath(path)) {
            log.debug("Public path, skipping auth: {}", path);
            return chain.filter(exchange);
        }

        
        // Get Authorization header
        String authHeader = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);
        
        // Check header presence
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for: {}", path);
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }
        
        // Extract token
        String token = authHeader.substring(7);  // Remove "Bearer " prefix
        
        // Validate token
        Claims claims = jwtUtil.validateToken(token);
        
        if (claims == null || jwtUtil.isTokenExpired(claims)) {
            log.warn("Invalid or expired token for: {}", path);
            return unauthorized(exchange, "Invalid or expired token");
        }

        
        // Add user info to request headers for downstream services
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
            .header("X-User-ID", jwtUtil.getUserId(claims))
            .header("X-User-Email", jwtUtil.getEmail(claims))
            .header("X-User-Role", jwtUtil.getRole(claims))
            .build();
        
        log.debug("Authenticated user: {} for path: {}", 
            jwtUtil.getEmail(claims), path);
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    /**
     * Check if path is public (no auth required).
     */
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
            .anyMatch(publicPath -> path.startsWith(publicPath));
    }

    
    /**
     * Return 401 Unauthorized response.
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Error-Message", message);
        return exchange.getResponse().setComplete();
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;  // After RateLimitFilter
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
 :: Spring Boot ::                (v3.2.x)

INFO  --- JwtUtil : JWT public key loaded successfully
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
# Look for: X-Correlation-ID: <uuid>

# Test 4: Test protected route without token
curl http://localhost:8080/api/v1/merchants
# Expected: 401 Unauthorized

# Test 5: Test rate limiting (run many times quickly)
for i in {1..110}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health; done
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
│       │               ├── filter/
│       │               │   ├── CorrelationIdFilter.java
│       │               │   ├── RateLimitFilter.java
│       │               │   └── JwtAuthenticationFilter.java
│       │               └── util/
│       │                   └── JwtUtil.java
│       └── resources/
│           ├── application.yml
│           └── keys/
│               ├── private.pem
│               └── public.pem
```


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
│  ✅ Filter Chain                                                            │
│     • GlobalFilter interface                                                │
│     • Ordered execution (HIGHEST_PRECEDENCE runs first)                    │
│     • Pre-filters run before routing, post-filters run after               │
│                                                                              │
│  ✅ JWT Validation                                                          │
│     • RSA key pair (public/private)                                        │
│     • Gateway only needs public key to verify                              │
│     • Extract claims and pass to downstream services                       │
│                                                                              │
│  ✅ Rate Limiting with Redis                                                │
│     • Fixed window counter algorithm                                        │
│     • Reactive Redis operations                                             │
│     • Fail-open on Redis errors (resilience)                               │
│                                                                              │
│  ✅ Correlation IDs                                                         │
│     • Distributed tracing                                                   │
│     • UUID per request                                                      │
│     • Passed through headers to all services                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Q&A / Troubleshooting

### Q1: Why does the Gateway use WebFlux instead of MVC?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Spring Cloud Gateway is built on WebFlux (reactive) for performance:      │
│                                                                              │
│  MVC (blocking):                  WebFlux (non-blocking):                   │
│  ────────────────                 ──────────────────────                    │
│  1 request = 1 thread             1 thread handles many requests            │
│  Thread waits during I/O          Thread never waits, uses callbacks        │
│                                                                              │
│  For a gateway that proxies requests to multiple services,                  │
│  non-blocking is essential for high throughput.                             │
└─────────────────────────────────────────────────────────────────────────────┘
```


### Q2: "Cannot load JWT public key" error

**Cause:** Key file not found or wrong format.

**Fix:**
```powershell
# Check file exists
dir api-gateway\src\main\resources\keys\public.pem

# Regenerate if needed
openssl genrsa -out api-gateway\src\main\resources\keys\private.pem 2048
openssl rsa -in api-gateway\src\main\resources\keys\private.pem -pubout -out api-gateway\src\main\resources\keys\public.pem
```

### Q3: "Connection refused" when Gateway starts

**Cause:** Config Server or Service Registry not running.

**Fix:**
```powershell
# Start Service Registry first
cd service-registry && mvn spring-boot:run

# Then Config Server
cd config-server && mvn spring-boot:run

# Wait 30 seconds, then start Gateway
cd api-gateway && mvn spring-boot:run
```

### Q4: Rate limit always returns 429

**Cause:** Redis not running or wrong configuration.

**Fix:**
```powershell
# Check Redis is running
docker ps | grep redis

# Start Redis if not running
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Verify connection
redis-cli ping
# Expected: PONG
```


### Q5: Gateway doesn't show in Eureka dashboard

**Cause:** Eureka registration failed.

**Fix:**
1. Check Config Server is returning correct config:
   ```powershell
   curl http://localhost:8888/api-gateway/default
   ```
2. Verify `eureka.client.service-url.defaultZone` points to correct URL
3. Check Gateway logs for registration errors

### Q6: CORS errors in browser

**Cause:** Frontend origin not in allowed-origins list.

**Fix:**
Add your frontend URL to `application.yml`:
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:5173"  # Your frontend URL
              - "http://localhost:3000"
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS TO EXPLORE                                 │
│                                                                              │
│  Circuit Breaker (Resilience4j)                                             │
│  ─────────────────────────────                                              │
│  When downstream service fails repeatedly, circuit breaker "opens"          │
│  and returns fallback response instead of waiting for timeout.              │
│  → Part 04 covers this                                                      │
│                                                                              │
│  OAuth 2.0 / OpenID Connect                                                 │
│  ──────────────────────────                                                 │
│  Industry standard for authorization. JWT is often used as the             │
│  access token format in OAuth 2.0 flows.                                   │
│  → Identity Service uses this (Parts 04-09)                                │
│                                                                              │
│  Service Mesh (Istio, Linkerd)                                              │
│  ─────────────────────────────                                              │
│  Alternative approach: Push gateway functionality to sidecar proxies.       │
│  Each service gets its own proxy for auth, rate limiting, etc.             │
│  → Advanced topic for Kubernetes deployments                               │
│                                                                              │
│  Token Bucket vs Fixed Window Rate Limiting                                 │
│  ────────────────────────────────────────                                   │
│  Fixed window (our implementation) has boundary problem.                    │
│  Token bucket is smoother but more complex.                                │
│  → Redis Rate Limiter plugin uses Token Bucket                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT'S NEXT                                               │
│                                                                              │
│  ✅ Part 03 COMPLETE: API Gateway                                           │
│                                                                              │
│  NEXT: Part 04 - Identity Service Setup                                     │
│  ─────────────────────────────────────                                      │
│  In Part 04, we'll create the Identity Service which:                       │
│  • Generates JWT tokens (uses private key)                                  │
│  • Handles user registration and login                                      │
│  • Manages user sessions                                                    │
│                                                                              │
│  The Identity Service creates the tokens that the API Gateway validates.   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                      │   │
│  │  User Login                                                          │   │
│  │      │                                                               │   │
│  │      ▼                                                               │   │
│  │  API Gateway ──────► Identity Service                               │   │
│  │      │                     │                                         │   │
│  │      │              Signs JWT with                                   │   │
│  │      │              PRIVATE key                                      │   │
│  │      │                     │                                         │   │
│  │      ◄─────────────────────┘                                        │   │
│  │      │                                                               │   │
│  │  Subsequent requests:                                                │   │
│  │  Gateway validates                                                   │   │
│  │  JWT with PUBLIC key                                                 │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Continue to: part-04-identity-service-setup.md                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 03 Complete!** 🎉

You now have a fully functional API Gateway with:
- Request routing to backend services
- JWT authentication for protected routes
- Rate limiting with Redis
- Correlation ID tracking
- CORS configuration
