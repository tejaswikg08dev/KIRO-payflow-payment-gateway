# Sprint 1, Part 02: API Gateway

**Duration:** 3-4 hours  
**Prerequisites:** Part 01 completed (Service Registry & Config Server running)

---

## 1. What We're Building

In this part, you'll build the API Gateway - the single entry point for all client requests:

| Feature | Purpose |
|---------|---------|
| Request Routing | Route requests to correct microservice |
| JWT Validation | Verify authentication tokens |
| Rate Limiting | Prevent abuse |
| Correlation ID | Track requests across services |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       API GATEWAY OVERVIEW                                   │
│                                                                              │
│                    ┌─────────────────────────────────────┐                  │
│     Client ──────► │          API GATEWAY                │                  │
│     Request        │          (Port 8080)                │                  │
│                    │                                     │                  │
│                    │  ┌─────────┐ ┌─────────┐ ┌───────┐ │                  │
│                    │  │  Rate   │ │   JWT   │ │ Route │ │                  │
│                    │  │ Limiter │►│ Filter  │►│Handler│ │                  │
│                    │  └─────────┘ └─────────┘ └───────┘ │                  │
│                    └──────────────────┬──────────────────┘                  │
│                                       │                                      │
│                    ┌──────────────────┼──────────────────┐                  │
│                    │                  │                  │                  │
│                    ▼                  ▼                  ▼                  │
│             ┌───────────┐      ┌───────────┐      ┌───────────┐            │
│             │ Identity  │      │ Merchant  │      │ Payment   │            │
│             │ Service   │      │ Service   │      │ Service   │            │
│             └───────────┘      └───────────┘      └───────────┘            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 What is an API Gateway?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WITHOUT API GATEWAY                                       │
│                                                                              │
│  Client needs to know ALL service URLs:                                      │
│                                                                              │
│  ┌────────┐                                                                 │
│  │ Client │───► http://server1:8081/users      (Identity)                   │
│  │        │───► http://server2:8082/merchants  (Merchant)                   │
│  │        │───► http://server3:8083/payments   (Payment)                    │
│  │        │───► http://server4:8084/webhooks   (Webhook)                    │
│  └────────┘                                                                 │
│                                                                              │
│  Problems:                                                                   │
│  ❌ Client needs to manage multiple URLs                                    │
│  ❌ CORS issues (different origins)                                         │
│  ❌ No central security                                                     │
│  ❌ Can't change service ports without updating clients                     │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                    WITH API GATEWAY                                          │
│                                                                              │
│  Client calls ONE URL, Gateway routes internally:                           │
│                                                                              │
│  ┌────────┐         ┌──────────────┐                                       │
│  │ Client │────────►│  API Gateway │───► Identity Service                   │
│  │        │         │  :8080       │───► Merchant Service                   │
│  │        │         │              │───► Payment Service                    │
│  │        │         │              │───► Webhook Service                    │
│  └────────┘         └──────────────┘                                       │
│                                                                              │
│  Benefits:                                                                   │
│  ✅ Single URL for clients                                                  │
│  ✅ Central authentication                                                  │
│  ✅ Rate limiting                                                           │
│  ✅ Request/Response logging                                                │
│  ✅ Protocol translation                                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Spring Cloud Gateway Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 SPRING CLOUD GATEWAY REQUEST FLOW                            │
│                                                                              │
│  Request: POST /v1/merchants                                                 │
│     │                                                                        │
│     ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     GATEWAY HANDLER MAPPING                          │   │
│  │  "Which route matches /v1/merchants?"                                │   │
│  │                                                                      │   │
│  │  Routes:                                                             │   │
│  │  ├─ /v1/auth/**      → identity-service    (no auth)                │   │
│  │  ├─ /v1/merchants/** → merchant-service    (auth required) ✓ MATCH  │   │
│  │  └─ /v1/payments/**  → payment-service     (auth required)          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                        │
│     ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     FILTER CHAIN (PRE-FILTERS)                       │   │
│  │                                                                      │   │
│  │  1. CorrelationIdFilter                                              │   │
│  │     → Add X-Correlation-ID header for tracing                        │   │
│  │                                                                      │   │
│  │  2. RateLimitFilter                                                  │   │
│  │     → Check: Is client over request limit?                           │   │
│  │     → If yes: Return 429 Too Many Requests                           │   │
│  │                                                                      │   │
│  │  3. JwtAuthenticationFilter                                          │   │
│  │     → Extract JWT from Authorization header                          │   │
│  │     → Validate signature and expiration                              │   │
│  │     → If invalid: Return 401 Unauthorized                            │   │
│  │     → If valid: Add X-User-Id header                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                        │
│     ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     PROXY TO SERVICE                                 │   │
│  │                                                                      │   │
│  │  Gateway → lb://merchant-service/v1/merchants                        │   │
│  │                                                                      │   │
│  │  "lb://" = Ask Eureka for address, load balance if multiple          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                        │
│     ▼                                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     FILTER CHAIN (POST-FILTERS)                      │   │
│  │                                                                      │   │
│  │  1. ResponseLoggingFilter                                            │   │
│  │     → Log response status and time                                   │   │
│  │                                                                      │   │
│  │  2. Add response headers (CORS, etc.)                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│     │                                                                        │
│     ▼                                                                        │
│  Response to Client                                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.3 JWT Validation in Gateway

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JWT VALIDATION STRATEGY                                   │
│                                                                              │
│  Option 1: Validate in EACH service (❌ Not recommended)                    │
│  ┌────────────────────────────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  Gateway                                                               ││
│  │  (just routes)                                                         ││
│  │      │                                                                 ││
│  │      ├────► Identity Service ──► Validate JWT ──► Process request      ││
│  │      ├────► Merchant Service ──► Validate JWT ──► Process request      ││
│  │      └────► Payment Service  ──► Validate JWT ──► Process request      ││
│  │                                                                        ││
│  │  Problems:                                                             ││
│  │  ❌ JWT validation code duplicated in every service                    ││
│  │  ❌ Public key must be in every service                                ││
│  │  ❌ Invalid requests still reach services (waste resources)            ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  Option 2: Validate in GATEWAY only (✅ Recommended)                        │
│  ┌────────────────────────────────────────────────────────────────────────┐│
│  │                                                                        ││
│  │  Gateway                                                               ││
│  │  ┌──────────────────┐                                                  ││
│  │  │ 1. Validate JWT  │ ──► Invalid? Return 401                          ││
│  │  │ 2. Extract user  │                                                  ││
│  │  │ 3. Add headers   │                                                  ││
│  │  └──────────────────┘                                                  ││
│  │      │                                                                 ││
│  │      │ (Only valid requests pass)                                      ││
│  │      │ Headers: X-User-Id, X-User-Role                                 ││
│  │      │                                                                 ││
│  │      ├────► Identity Service ──► Trust headers ──► Process             ││
│  │      ├────► Merchant Service ──► Trust headers ──► Process             ││
│  │      └────► Payment Service  ──► Trust headers ──► Process             ││
│  │                                                                        ││
│  │  Benefits:                                                             ││
│  │  ✅ JWT validation in ONE place                                        ││
│  │  ✅ Services trust Gateway (internal network)                          ││
│  │  ✅ Invalid requests blocked early                                     ││
│  │  ✅ Services just read X-User-Id header                                ││
│  └────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.4 RSA Key Pair for JWT

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RSA KEY PAIR FOR JWT (RS256)                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  PRIVATE KEY (Keep secret!)                                          │   │
│  │  ─────────────────────────────                                       │   │
│  │  • Only Identity Service has this                                    │   │
│  │  • Used to SIGN (create) tokens                                      │   │
│  │  • Never share, never commit to Git                                  │   │
│  │                                                                      │   │
│  │  Location: identity-service/src/main/resources/keys/private.pem     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  PUBLIC KEY (Can be shared)                                          │   │
│  │  ────────────────────────────                                        │   │
│  │  • API Gateway and all services have this                           │   │
│  │  • Used to VERIFY (validate) tokens                                  │   │
│  │  • Safe to share, can be in Git                                     │   │
│  │                                                                      │   │
│  │  Location: api-gateway/src/main/resources/keys/public.pem           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Workflow:                                                                   │
│  ┌─────────────────┐         ┌─────────────────┐                           │
│  │ Identity Service│         │   API Gateway   │                           │
│  │                 │         │                 │                           │
│  │ 1. User logs in │         │ 4. Verify token │                           │
│  │ 2. Create JWT   │         │    with PUBLIC  │                           │
│  │    with PRIVATE │ ──────► │    key          │                           │
│  │    key          │  token  │                 │                           │
│  │ 3. Return token │         │ 5. If valid,    │                           │
│  │    to client    │         │    forward to   │                           │
│  │                 │         │    service      │                           │
│  └─────────────────┘         └─────────────────┘                           │
│                                                                              │
│  Why RS256 instead of HS256?                                                │
│  • HS256 (HMAC): One shared secret for sign AND verify                     │
│    → Every service needs the secret (security risk)                         │
│  • RS256 (RSA): Private key to sign, public key to verify                  │
│    → Only auth service has private key (more secure)                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Before starting, verify Part 01 services are running:

```powershell
# Check Eureka is running
curl http://localhost:8761/actuator/health
# Expected: {"status":"UP"}

# Check Config Server is running
curl http://localhost:8888/actuator/health
# Expected: {"status":"UP"}

# Check CONFIG-SERVER is registered in Eureka
# Open http://localhost:8761 in browser
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

Add api-gateway module to parent `pom.xml`:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>
    <module>api-gateway</module>        <!-- ADD THIS -->
</modules>
```

---

### Step 4.2: Create API Gateway Module

**Create folder structure:**

```powershell
# Create api-gateway folder
mkdir api-gateway
mkdir api-gateway\src\main\java\com\payflow\gateway
mkdir api-gateway\src\main\java\com\payflow\gateway\filter
mkdir api-gateway\src\main\java\com\payflow\gateway\config
mkdir api-gateway\src\main\java\com\payflow\gateway\util
mkdir api-gateway\src\main\resources
mkdir api-gateway\src\main\resources\keys
```

---

### Step 4.3: Create api-gateway/pom.xml

Create `api-gateway/pom.xml`:

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
    <description>API Gateway with routing and JWT validation</description>

    <dependencies>
        <!-- 
        Spring Cloud Gateway
        What it does:
        - Non-blocking, reactive gateway
        - Route matching with predicates
        - Filter chain for request/response modification
        
        IMPORTANT: Gateway uses WebFlux (reactive), NOT Spring MVC!
        Don't add spring-boot-starter-web here.
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- 
        Eureka Client
        For service discovery (lb://service-name routing)
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- 
        Config Client
        Fetch configuration from Config Server
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>

        <!-- 
        JJWT - Java JWT Library
        For JWT parsing and validation
        -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>

        <!-- 
        Spring Boot Actuator
        Health checks, metrics
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- 
        Redis for Rate Limiting
        Stores rate limit counters
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
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

---

### Step 4.4: Generate RSA Key Pair

Generate the RSA key pair for JWT signing/verification:

```powershell
# Navigate to api-gateway resources
cd api-gateway\src\main\resources\keys

# Generate private key (2048-bit RSA)
openssl genrsa -out private.pem 2048

# Extract public key from private key
openssl rsa -in private.pem -pubout -out public.pem

# Verify keys were created
dir
# Should show: private.pem, public.pem
```

**Expected file contents:**

`private.pem` (example - yours will be different):
```
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC7...
(many lines of base64)
-----END PRIVATE KEY-----
```

`public.pem` (example):
```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu...
(a few lines of base64)
-----END PUBLIC KEY-----
```

**Copy private.pem to identity-service** (we'll create this folder later):

```powershell
# Create identity-service keys folder
mkdir ..\..\..\..\identity-service\src\main\resources\keys

# Copy private key
copy private.pem ..\..\..\..\identity-service\src\main\resources\keys\

# Remove private key from gateway (it only needs public key)
del private.pem
```

**IMPORTANT:** The private key should ONLY be in identity-service!


---

### Step 4.5: Create ApiGatewayApplication.java

Create `api-gateway/src/main/java/com/payflow/gateway/ApiGatewayApplication.java`:

```java
package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 * 
 * The single entry point for all client requests.
 * Routes requests to appropriate microservices and handles:
 * - JWT authentication
 * - Rate limiting
 * - Request/response logging
 * - Correlation ID tracking
 * 
 * Note: This is a REACTIVE application using Spring WebFlux.
 * It does NOT use Spring MVC's @RestController pattern.
 * Routes are defined in application.yml configuration.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

---

### Step 4.6: Create JwtUtil.java

Create `api-gateway/src/main/java/com/payflow/gateway/util/JwtUtil.java`:

```java
package com.payflow.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT Utility for validating tokens
 * 
 * This class handles:
 * 1. Loading the RSA public key
 * 2. Validating JWT signatures
 * 3. Extracting claims (user info) from tokens
 * 
 * IMPORTANT: Gateway only has PUBLIC key.
 * It can VERIFY tokens but cannot CREATE them.
 * Only Identity Service has the PRIVATE key to create tokens.
 */
@Component
@Slf4j
public class JwtUtil {

    // ═══════════════════════════════════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Path to public key file
     * Loaded from application.yml: jwt.public-key-path
     */
    @Value("${jwt.public-key-path:classpath:keys/public.pem}")
    private Resource publicKeyResource;

    /**
     * The loaded RSA public key
     */
    private PublicKey publicKey;

    // ═══════════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Load the public key after bean creation
     * 
     * @PostConstruct runs once after all dependencies are injected.
     * We load the key here so it's ready for all requests.
     */
    @PostConstruct
    public void init() {
        try {
            this.publicKey = loadPublicKey();
            log.info("✓ JWT public key loaded successfully");
        } catch (Exception e) {
            log.error("✗ Failed to load JWT public key: {}", e.getMessage());
            throw new RuntimeException("Could not load public key", e);
        }
    }

    /**
     * Load RSA public key from PEM file
     * 
     * PEM format:
     * -----BEGIN PUBLIC KEY-----
     * MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
     * -----END PUBLIC KEY-----
     * 
     * We need to:
     * 1. Read the file
     * 2. Remove the header/footer lines
     * 3. Base64 decode the content
     * 4. Create a PublicKey object
     */
    private PublicKey loadPublicKey() throws Exception {
        // Read PEM file content
        String keyContent = new String(
            Files.readAllBytes(publicKeyResource.getFile().toPath()),
            StandardCharsets.UTF_8
        );
        
        // Remove PEM headers and whitespace
        // "-----BEGIN PUBLIC KEY-----" and "-----END PUBLIC KEY-----"
        keyContent = keyContent
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");  // Remove all whitespace
        
        // Base64 decode
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        
        // Create PublicKey from bytes
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Token Validation
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Validate a JWT token
     * 
     * Checks:
     * 1. Token format is valid
     * 2. Signature matches (using public key)
     * 3. Token has not expired
     * 
     * @param token The JWT token (without "Bearer " prefix)
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            // Parse and validate token
            // This throws exception if invalid
            Jwts.parser()
                .verifyWith(publicKey)  // Use public key to verify signature
                .build()
                .parseSignedClaims(token);
            
            return true;
            
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            return false;
            
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT format: {}", e.getMessage());
            return false;
            
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            return false;
            
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Claim Extraction
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Get all claims from token
     * 
     * Claims are the payload data in the JWT:
     * {
     *   "sub": "user-id-uuid",
     *   "email": "user@example.com",
     *   "role": "MERCHANT",
     *   "iat": 1234567890,
     *   "exp": 1234567890
     * }
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Get user ID from token
     * The "sub" (subject) claim contains the user ID
     */
    public String getUserId(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Get user email from token
     */
    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * Get user role from token
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }
}
```

---

### Step 4.7: Create JwtAuthenticationFilter.java

Create `api-gateway/src/main/java/com/payflow/gateway/filter/JwtAuthenticationFilter.java`:

```java
package com.payflow.gateway.filter;

import com.payflow.gateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Authentication Filter
 * 
 * This filter:
 * 1. Extracts JWT from Authorization header
 * 2. Validates the token
 * 3. Adds user info to request headers for downstream services
 * 4. Returns 401 if token is invalid
 * 
 * Usage in routes:
 * filters:
 *   - JwtAuthentication
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends 
        AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    /**
     * Configuration class for the filter
     * Can be extended to add configurable options
     */
    public static class Config {
        // Configuration properties can be added here
        // For example: roles required, paths to skip, etc.
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // ═══════════════════════════════════════════════════════════════
            // Step 1: Extract Authorization header
            // ═══════════════════════════════════════════════════════════════
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            
            // Check if header exists
            if (authHeader == null || authHeader.isEmpty()) {
                log.warn("No Authorization header found");
                return onError(exchange, "No Authorization header", HttpStatus.UNAUTHORIZED);
            }
            
            // Check if it's a Bearer token
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("Invalid Authorization header format");
                return onError(exchange, "Invalid Authorization format", HttpStatus.UNAUTHORIZED);
            }
            
            // ═══════════════════════════════════════════════════════════════
            // Step 2: Extract token (remove "Bearer " prefix)
            // ═══════════════════════════════════════════════════════════════
            String token = authHeader.substring(7);  // "Bearer ".length() = 7
            
            // ═══════════════════════════════════════════════════════════════
            // Step 3: Validate token
            // ═══════════════════════════════════════════════════════════════
            if (!jwtUtil.validateToken(token)) {
                log.warn("Invalid JWT token");
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }
            
            // ═══════════════════════════════════════════════════════════════
            // Step 4: Extract user info and add to headers
            // ═══════════════════════════════════════════════════════════════
            String userId = jwtUtil.getUserId(token);
            String email = jwtUtil.getEmail(token);
            String role = jwtUtil.getRole(token);
            
            log.debug("JWT validated for user: {} ({})", email, userId);
            
            // Create modified request with user info headers
            ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .build();
            
            // ═══════════════════════════════════════════════════════════════
            // Step 5: Continue filter chain with modified request
            // ═══════════════════════════════════════════════════════════════
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    /**
     * Handle authentication errors
     * 
     * Returns a 401 Unauthorized response with error message
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        
        String body = String.format(
            "{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}",
            status.name(),
            message
        );
        
        return response.writeWith(Mono.just(
            response.bufferFactory().wrap(body.getBytes())
        ));
    }
}
```


---

### Step 4.8: Create CorrelationIdFilter.java

Create `api-gateway/src/main/java/com/payflow/gateway/filter/CorrelationIdFilter.java`:

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
 * Correlation ID Filter
 * 
 * Adds a unique X-Correlation-ID header to every request.
 * This ID is used to trace requests across all microservices.
 * 
 * Why is this important?
 * - When debugging issues in production, you need to find logs
 *   related to a specific request across multiple services
 * - The correlation ID links all logs together
 * 
 * Example:
 * Request to Gateway → Payment Service → Bank Simulator
 * All logs will have: X-Correlation-ID: abc-123-xyz
 * You can search logs by this ID to see the full request flow
 */
@Component
@Slf4j
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Check if correlation ID already exists (from client)
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        
        // If not present, generate a new one
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        final String finalCorrelationId = correlationId;
        
        // Add correlation ID to request headers
        ServerHttpRequest modifiedRequest = request.mutate()
            .header(CORRELATION_ID_HEADER, finalCorrelationId)
            .build();
        
        // Log the request with correlation ID
        log.info("[{}] {} {}", 
            finalCorrelationId,
            request.getMethod(),
            request.getURI().getPath()
        );
        
        // Add correlation ID to response headers too
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);
        
        // Continue filter chain
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * Order determines when this filter runs in the chain.
     * Lower number = runs earlier.
     * We want this to run FIRST so all other filters have the correlation ID.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // Run first
    }
}
```

---

### Step 4.9: Create RateLimitFilter.java

Create `api-gateway/src/main/java/com/payflow/gateway/filter/RateLimitFilter.java`:

```java
package com.payflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Rate Limiting Filter
 * 
 * Limits the number of requests a client can make in a time window.
 * Uses Redis to track request counts (works across multiple gateway instances).
 * 
 * Configuration:
 * - requests-per-second: Max requests allowed per second per client
 * 
 * Identification:
 * - Uses client IP address as identifier
 * - In production, you might use API key or user ID
 */
@Component
@Slf4j
public class RateLimitFilter extends AbstractGatewayFilterFactory<RateLimitFilter.Config> {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    /**
     * Configuration for rate limiting
     */
    public static class Config {
        private int requestsPerSecond = 100;  // Default: 100 requests/second
        
        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }
        
        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Get client identifier (IP address)
            String clientId = getClientId(exchange);
            String rateLimitKey = "rate_limit:" + clientId;
            
            // Increment counter in Redis
            return redisTemplate.opsForValue()
                .increment(rateLimitKey)
                .flatMap(count -> {
                    // Set expiry on first request
                    if (count == 1) {
                        return redisTemplate.expire(rateLimitKey, Duration.ofSeconds(1))
                            .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    // Check if over limit
                    if (count > config.getRequestsPerSecond()) {
                        log.warn("Rate limit exceeded for client: {} (count: {})", 
                            clientId, count);
                        return onRateLimitExceeded(exchange);
                    }
                    
                    // Add rate limit headers
                    exchange.getResponse().getHeaders()
                        .add("X-RateLimit-Limit", String.valueOf(config.getRequestsPerSecond()));
                    exchange.getResponse().getHeaders()
                        .add("X-RateLimit-Remaining", 
                            String.valueOf(config.getRequestsPerSecond() - count));
                    
                    // Continue
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    // If Redis is down, allow the request (fail open)
                    log.warn("Rate limiting failed (Redis error): {}", e.getMessage());
                    return chain.filter(exchange);
                });
        };
    }

    /**
     * Get client identifier from request
     * Uses IP address, but could use API key or user ID
     */
    private String getClientId(ServerWebExchange exchange) {
        // Try X-Forwarded-For header first (if behind load balancer)
        String forwardedFor = exchange.getRequest().getHeaders()
            .getFirst("X-Forwarded-For");
        
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Take first IP if multiple
            return forwardedFor.split(",")[0].trim();
        }
        
        // Fall back to remote address
        return Objects.requireNonNull(
            exchange.getRequest().getRemoteAddress()
        ).getAddress().getHostAddress();
    }

    /**
     * Handle rate limit exceeded
     */
    private Mono<Void> onRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("Retry-After", "1");  // Retry after 1 second
        
        String body = "{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\"," +
            "\"message\":\"Too many requests. Please slow down.\"}}";
        
        return response.writeWith(Mono.just(
            response.bufferFactory().wrap(body.getBytes())
        ));
    }
}
```

---

### Step 4.10: Create application.yml

Create `api-gateway/src/main/resources/application.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# API Gateway Configuration
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8080

spring:
  application:
    name: api-gateway
    
  # ─────────────────────────────────────────────────────────────────────────
  # Config Server
  # Fetch additional config from Config Server
  # ─────────────────────────────────────────────────────────────────────────
  config:
    import: optional:configserver:http://localhost:8888
    
  # ─────────────────────────────────────────────────────────────────────────
  # Cloud Gateway Routes
  # Define how requests are routed to services
  # ─────────────────────────────────────────────────────────────────────────
  cloud:
    gateway:
      # ─────────────────────────────────────────────────────────────────────
      # Default filters apply to ALL routes
      # ─────────────────────────────────────────────────────────────────────
      default-filters:
        - name: RateLimit
          args:
            requests-per-second: 100
            
      # ─────────────────────────────────────────────────────────────────────
      # Route definitions
      # ─────────────────────────────────────────────────────────────────────
      routes:
        # ═══════════════════════════════════════════════════════════════════
        # AUTH ROUTES (No JWT required - public endpoints)
        # ═══════════════════════════════════════════════════════════════════
        - id: auth-routes
          uri: lb://identity-service
          predicates:
            - Path=/v1/auth/**
          # No JwtAuthentication filter here - these are public!
          
        # ═══════════════════════════════════════════════════════════════════
        # MERCHANT ROUTES (JWT required)
        # ═══════════════════════════════════════════════════════════════════
        - id: merchant-routes
          uri: lb://merchant-service
          predicates:
            - Path=/v1/merchants/**
          filters:
            - JwtAuthentication  # Requires valid JWT
            
        # ═══════════════════════════════════════════════════════════════════
        # PAYMENT ROUTES (JWT required) - Will be used in Sprint 3
        # ═══════════════════════════════════════════════════════════════════
        - id: payment-routes
          uri: lb://payment-service
          predicates:
            - Path=/v1/payments/**, /v1/orders/**
          filters:
            - JwtAuthentication
            
        # ═══════════════════════════════════════════════════════════════════
        # USER PROFILE ROUTES (JWT required)
        # ═══════════════════════════════════════════════════════════════════
        - id: user-profile-routes
          uri: lb://identity-service
          predicates:
            - Path=/v1/users/**
          filters:
            - JwtAuthentication

# ─────────────────────────────────────────────────────────────────────────────
# Eureka Client Configuration
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# ─────────────────────────────────────────────────────────────────────────────
# JWT Configuration
# ─────────────────────────────────────────────────────────────────────────────
jwt:
  public-key-path: classpath:keys/public.pem

# ─────────────────────────────────────────────────────────────────────────────
# Redis Configuration (for rate limiting)
# ─────────────────────────────────────────────────────────────────────────────
spring.data.redis:
  host: localhost
  port: 6379

# ─────────────────────────────────────────────────────────────────────────────
# Actuator Configuration
# ─────────────────────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
  endpoint:
    gateway:
      enabled: true

# ─────────────────────────────────────────────────────────────────────────────
# Logging
# ─────────────────────────────────────────────────────────────────────────────
logging:
  level:
    com.payflow.gateway: DEBUG
    org.springframework.cloud.gateway: DEBUG
```


---

### Step 4.11: Create Dockerfile

Create `api-gateway/Dockerfile`:

```dockerfile
# ═══════════════════════════════════════════════════════════════════════════
# API Gateway Dockerfile
# Multi-stage build for smaller final image
# ═══════════════════════════════════════════════════════════════════════════

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/target/api-gateway-*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 5. Verification

### 5.1 Build and Run

```powershell
# Make sure Service Registry and Config Server are running first!

# Build API Gateway
cd api-gateway
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected output:**
```
Started ApiGatewayApplication in X.XXX seconds
✓ JWT public key loaded successfully
```

### 5.2 Test Gateway

**Test health endpoint:**
```powershell
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**Check Eureka registration:**
Open http://localhost:8761 - You should see API-GATEWAY in the list.

**Test routing (will fail until Identity Service is built):**
```powershell
curl http://localhost:8080/v1/auth/health
# Expected: 503 Service Unavailable (identity-service not running yet)
```

**Test JWT protection:**
```powershell
# Try to access protected route without JWT
curl http://localhost:8080/v1/merchants
# Expected: 401 Unauthorized - "No Authorization header"

# Try with invalid JWT
curl http://localhost:8080/v1/merchants -H "Authorization: Bearer invalid"
# Expected: 401 Unauthorized - "Invalid or expired token"
```

### 5.3 Test Correlation ID

```powershell
curl -v http://localhost:8080/actuator/health
# Look for X-Correlation-ID in response headers
```

### 5.4 Verification Checklist

| Check | Command | Expected Result |
|-------|---------|-----------------|
| Gateway running | http://localhost:8080/actuator/health | `{"status":"UP"}` |
| Registered with Eureka | http://localhost:8761 | API-GATEWAY in list |
| JWT filter works | curl without token to protected route | 401 Unauthorized |
| Correlation ID added | Check response headers | X-Correlation-ID present |
| Rate limit headers | Check response headers | X-RateLimit-* headers |

---

## 6. File Structure After This Part

```
payflow-payment-gateway/
├── pom.xml                           (updated with api-gateway module)
├── common-lib/
├── service-registry/
├── config-server/
│
└── api-gateway/                      ← NEW!
    ├── pom.xml
    ├── Dockerfile
    └── src/main/
        ├── java/com/payflow/gateway/
        │   ├── ApiGatewayApplication.java
        │   ├── config/
        │   ├── filter/
        │   │   ├── JwtAuthenticationFilter.java
        │   │   ├── CorrelationIdFilter.java
        │   │   └── RateLimitFilter.java
        │   └── util/
        │       └── JwtUtil.java
        └── resources/
            ├── application.yml
            └── keys/
                └── public.pem
```

---

## 7. Key Takeaways

### Gateway Concepts

| Concept | What It Does | Why It Matters |
|---------|--------------|----------------|
| Routing | Maps URLs to services | Single entry point |
| Filter Chain | Process requests/responses | Modular middleware |
| Load Balancing | Distribute requests | Handle scale |
| Circuit Breaker | Handle service failures | Fault tolerance |

### JWT in Gateway

| Step | What Happens | Result |
|------|--------------|--------|
| Extract | Get token from header | "Bearer xxx" → "xxx" |
| Validate | Check signature + expiry | Valid/Invalid |
| Enrich | Add user info to headers | X-User-Id, X-User-Role |
| Forward | Pass to downstream service | Service trusts gateway |

### Filter Order

| Filter | Order | Purpose |
|--------|-------|---------|
| CorrelationId | HIGHEST | Add tracing ID first |
| RateLimit | HIGH | Block abusive requests early |
| JwtAuthentication | DEFAULT | Validate auth |
| RouteToService | LOW | Actually route the request |

---

## 8. Common Issues & Solutions

### Issue 1: Public key not found

```
Error: Could not load public key
```

**Solution:**
- Check `keys/public.pem` exists in `src/main/resources/`
- Verify PEM format (starts with `-----BEGIN PUBLIC KEY-----`)
- Check file permissions

### Issue 2: Service discovery not working

```
Error: lb://identity-service returned 503
```

**Solution:**
- Check Eureka is running
- Check identity-service is registered
- Wait 30 seconds after service starts

### Issue 3: Redis connection refused

```
Error: Unable to connect to Redis
```

**Solution:**
- Check Redis is running: `docker ps | grep redis`
- If not, start it: `docker compose -f docker-compose.infra.yml up -d redis`

---

## 9. Next Steps

**API Gateway is ready!** You now have:
- ✅ Request routing to services
- ✅ JWT validation
- ✅ Rate limiting
- ✅ Correlation ID tracking

**Continue to:** [Part 03: Identity Service](./part-03-identity-service.md)

In Part 03, you'll build:
- User registration and login
- JWT token generation
- Password hashing with BCrypt
- User entity and repository

---

**End of Sprint 1, Part 02**

*Next: Identity Service for Authentication*
