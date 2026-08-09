# Sprint 1, Part 04: Identity Service Setup

**Duration:** 2-3 hours  
**Prerequisites:** Parts 01-03 completed, Service Registry, Config Server, and API Gateway running

---

## 1. What We're Building

In this part, you'll set up the **Identity Service** foundation - the service responsible for authentication.

| Component | Port | Purpose |
|-----------|------|---------|
| identity-service | 8081 | User authentication, JWT token generation |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     IDENTITY SERVICE ROLE                                    │
│                                                                              │
│  The Identity Service is the GATEKEEPER of your system.                     │
│  It answers: "Who is this user?"                                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    AUTHENTICATION FLOW                               │   │
│  │                                                                      │   │
│  │  1. User sends credentials                                          │   │
│  │     POST /api/v1/auth/login                                         │   │
│  │     { "email": "...", "password": "..." }                          │   │
│  │                         │                                            │   │
│  │                         ▼                                            │   │
│  │  2. Identity Service validates                                      │   │
│  │     • Check email exists in database                                │   │
│  │     • Compare password hash                                         │   │
│  │                         │                                            │   │
│  │                         ▼                                            │   │
│  │  3. If valid, generate JWT token                                    │   │
│  │     • Sign with PRIVATE key                                         │   │
│  │     • Include user ID, email, role                                  │   │
│  │     • Set expiration (15 min for access token)                     │   │
│  │                         │                                            │   │
│  │                         ▼                                            │   │
│  │  4. Return tokens to user                                           │   │
│  │     { "accessToken": "...", "refreshToken": "..." }                │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Later, when user makes API requests:                                        │
│  • API Gateway validates token with PUBLIC key (Part 03)                    │
│  • If valid, request proceeds to target service                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Authentication vs Authorization

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION vs AUTHORIZATION                           │
│                                                                              │
│  ┌────────────────────────────────┐  ┌────────────────────────────────┐    │
│  │       AUTHENTICATION           │  │       AUTHORIZATION            │    │
│  │       (AuthN)                  │  │       (AuthZ)                  │    │
│  │                                │  │                                │    │
│  │  "Who are you?"                │  │  "What can you do?"            │    │
│  │                                │  │                                │    │
│  │  Verifies IDENTITY:            │  │  Grants PERMISSIONS:           │    │
│  │  • Username/password           │  │  • Read data                   │    │
│  │  • Biometrics                  │  │  • Write data                  │    │
│  │  • MFA codes                   │  │  • Delete resources            │    │
│  │  • SSO tokens                  │  │  • Admin functions             │    │
│  │                                │  │                                │    │
│  │  Identity Service handles this │  │  Each service handles this     │    │
│  │                                │  │  based on user role            │    │
│  └────────────────────────────────┘  └────────────────────────────────┘    │
│                                                                              │
│  Example:                                                                    │
│  ────────                                                                   │
│  1. AuthN: "I'm john@example.com" (Identity Service verifies)              │
│  2. AuthZ: "John has MERCHANT role, can access /merchants/*"               │
│            (Merchant Service checks role)                                   │
│                                                                              │
│  In PayFlow:                                                                │
│  • Identity Service: Authentication (this part)                            │
│  • Each service: Authorization (check role from JWT)                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.2 JWT Token Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JWT (JSON Web Token) ANATOMY                              │
│                                                                              │
│  A JWT has three parts separated by dots:                                   │
│                                                                              │
│  xxxxx.yyyyy.zzzzz                                                          │
│  │     │     │                                                              │
│  │     │     └── SIGNATURE (verify token wasn't tampered)                  │
│  │     └──────── PAYLOAD (the claims - user info)                          │
│  └────────────── HEADER (algorithm info)                                   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  HEADER (Base64 encoded)                                             │   │
│  │  {                                                                   │   │
│  │    "alg": "HS256",    // Algorithm: HMAC with SHA-256              │   │
│  │    "typ": "JWT"       // Token type                                 │   │
│  │  }                                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  PAYLOAD (Base64 encoded) - NOT encrypted, just encoded!            │   │
│  │  {                                                                   │   │
│  │    "sub": "user-uuid-123",           // Subject (user ID)          │   │
│  │    "email": "john@example.com",      // Custom claim                │   │
│  │    "role": "MERCHANT",               // Custom claim                │   │
│  │    "iat": 1609459200,                // Issued at (timestamp)      │   │
│  │    "exp": 1609460100                 // Expires at (15 min later)  │   │
│  │  }                                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  SIGNATURE                                                           │   │
│  │                                                                      │   │
│  │  HMACSHA256(                                                         │   │
│  │    base64(header) + "." + base64(payload),                          │   │
│  │    secretKey                                                         │   │
│  │  )                                                                   │   │
│  │                                                                      │   │
│  │  Only someone with secret key can CREATE or VERIFY signature        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  IMPORTANT: Payload is NOT encrypted! Anyone can decode and read it.       │
│  Never put sensitive data (passwords, credit cards) in JWT!                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Access Token vs Refresh Token

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TOKEN TYPES                                               │
│                                                                              │
│  ┌────────────────────────────┐  ┌────────────────────────────┐            │
│  │     ACCESS TOKEN           │  │     REFRESH TOKEN          │            │
│  ├────────────────────────────┤  ├────────────────────────────┤            │
│  │ Purpose: API authorization │  │ Purpose: Get new access    │            │
│  │                            │  │          token             │            │
│  │ Lifetime: Short (15 min)   │  │ Lifetime: Long (7 days)    │            │
│  │                            │  │                            │            │
│  │ Stored: Memory (frontend)  │  │ Stored: HttpOnly cookie    │            │
│  │                            │  │         or secure storage  │            │
│  │ If stolen: Limited damage  │  │ If stolen: Can get new     │            │
│  │ (expires soon)             │  │ access tokens (revoke!)    │            │
│  └────────────────────────────┘  └────────────────────────────┘            │
│                                                                              │
│  WHY TWO TOKENS?                                                            │
│  ───────────────                                                            │
│  Security vs Convenience tradeoff:                                          │
│                                                                              │
│  • Long-lived access token = If stolen, attacker has access for long time  │
│  • Short-lived access token = User must login every 15 minutes (annoying)  │
│                                                                              │
│  Solution: Two tokens!                                                       │
│  • Access token expires quickly (limits damage if stolen)                   │
│  • Refresh token silently gets new access token (good UX)                  │
│                                                                              │
│  FLOW:                                                                       │
│  ─────                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 1. Login → Get access token (15 min) + refresh token (7 days)       │   │
│  │ 2. Use access token for API calls                                   │   │
│  │ 3. After 15 min, access token expires                              │   │
│  │ 4. Frontend detects 401, uses refresh token to get new access      │   │
│  │ 5. User continues without re-login                                  │   │
│  │ 6. After 7 days, refresh token expires → User must login again     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.4 Password Security

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PASSWORD HASHING                                          │
│                                                                              │
│  NEVER store plain text passwords!                                          │
│                                                                              │
│  What happens on registration:                                               │
│  ─────────────────────────────                                              │
│  User enters:    "MyPassword123"                                            │
│        │                                                                     │
│        ▼                                                                     │
│  BCrypt hash:    "$2a$10$N9qo8uLOickgx2ZMRZoMye..."                        │
│        │                                                                     │
│        ▼                                                                     │
│  Stored in DB:   Only the hash, not the password!                           │
│                                                                              │
│  What happens on login:                                                      │
│  ─────────────────────                                                      │
│  User enters:    "MyPassword123"                                            │
│        │                                                                     │
│        ▼                                                                     │
│  BCrypt.matches("MyPassword123", storedHash)                                │
│        │                                                                     │
│        ▼                                                                     │
│  Returns:        true (password matches) or false (wrong password)          │
│                                                                              │
│  WHY BCRYPT?                                                                 │
│  ──────────                                                                 │
│  • Salt: Each hash includes random salt (same password → different hashes) │
│  • Work factor: Intentionally slow (prevents brute force)                  │
│  • One-way: Cannot reverse hash to get password                            │
│                                                                              │
│  BCrypt hash format:                                                        │
│  $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy               │
│   ─── ── ──────────────────────────────────────────────────────            │
│    │  │         └── Hash value                                              │
│    │  └──────────── Salt (22 chars)                                        │
│    └─────────────── Cost factor (2^10 = 1024 iterations)                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Before starting, verify:

```powershell
# Terminal 1: Service Registry running
curl http://localhost:8761/actuator/health
# Expected: {"status":"UP"}

# Terminal 2: Config Server running
curl http://localhost:8888/actuator/health
# Expected: {"status":"UP"}

# Terminal 3: API Gateway running
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Terminal 4: PostgreSQL running
docker ps | findstr postgres
# Expected: postgres container running

# Check PostgreSQL connection and identity schema exists
docker exec -it postgres psql -U payflow -d payflow -c "SELECT 1"
# Expected: 1
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

**File: `pom.xml` (project root)**

Add identity-service to modules:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>
    <module>api-gateway</module>
    <module>identity-service</module>  <!-- ADD THIS LINE -->
</modules>
```


---

### Step 4.2: Create Folder Structure

```powershell
# Create the identity-service module structure
mkdir identity-service
mkdir identity-service\src\main\java\com\payflow\identity
mkdir identity-service\src\main\java\com\payflow\identity\config
mkdir identity-service\src\main\java\com\payflow\identity\controller
mkdir identity-service\src\main\java\com\payflow\identity\dto
mkdir identity-service\src\main\java\com\payflow\identity\model
mkdir identity-service\src\main\java\com\payflow\identity\repository
mkdir identity-service\src\main\java\com\payflow\identity\service
mkdir identity-service\src\main\resources
mkdir identity-service\src\main\resources\db\migration
mkdir identity-service\src\test\java\com\payflow\identity

# Verify structure
tree identity-service /F
```

Expected:
```
identity-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/payflow/identity/
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── model/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   │       └── db/migration/
│   └── test/
│       └── java/
│           └── com/payflow/identity/
└── pom.xml
```

---

### Step 4.3: Create pom.xml

**File: `identity-service/pom.xml`**

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

    <artifactId>identity-service</artifactId>
    <name>PayFlow Identity Service</name>
    <description>User registration, login, JWT authentication</description>

    <dependencies>
        <!-- Spring Web (REST controllers) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Security (authentication & authorization) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Spring Data JPA (database access) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway (database migrations) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- Validation (@NotNull, @Email, @Size) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- JWT library (create and validate tokens) -->
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

        <!-- Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Swagger / OpenAPI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- Our common library -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
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

**Note:** Lombok is defined in the parent pom.xml, so it doesn't need to be declared here. The version for `springdoc-openapi-starter-webmvc-ui` and `common-lib` are also managed in the parent pom.

---

### Step 4.4: Create Main Application Class

**File: `identity-service/src/main/java/com/payflow/identity/IdentityServiceApplication.java`**

```java
package com.payflow.identity;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Identity Service Application - Authentication and User Management.
 *
 * Key Endpoints:
 * - POST /v1/auth/register   - Create new user account
 * - POST /v1/auth/login      - Authenticate and get tokens
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.identity", "com.payflow.common"})
@OpenAPIDefinition(
        info = @Info(
                title = "PayFlow Identity Service API",
                version = "1.0",
                description = "User registration, login, and JWT token management",
                contact = @Contact(name = "PayFlow Team")
        )
)
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```

**Note:** 
- `@ComponentScan` includes both `com.payflow.identity` and `com.payflow.common` packages (for common-lib)
- `@OpenAPIDefinition` configures Swagger/OpenAPI documentation

---

### Step 4.5: Create application.yml

**File: `identity-service/src/main/resources/application.yml`**

```yaml
# Identity Service — Local configuration
# In production, config comes from Config Server.
# This file is used for local development (running without Config Server).

server:
  port: 8081

spring:
  application:
    name: identity-service
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=identity
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: identity
        format_sql: true
  flyway:
    enabled: true
    schemas: identity
    locations: classpath:db/migration

jwt:
  secret: payflow-jwt-secret-key-change-in-production-minimum-256-bits-long-key-here
  access-token-expiry: 900000
  refresh-token-expiry: 604800000

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

**Key Configuration Points:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION.YML EXPLAINED                                 │
│                                                                              │
│  DATABASE (Schema Isolation):                                               │
│  ─────────────────────────────                                              │
│  url: jdbc:postgresql://localhost:5432/payflow?currentSchema=identity       │
│       │                           │              │                          │
│       │                           │              └── Schema name           │
│       │                           └── Single shared database               │
│       └── PostgreSQL connection                                            │
│                                                                              │
│  JPA + FLYWAY:                                                              │
│  ─────────────                                                              │
│  • ddl-auto: validate      → Hibernate verifies entity matches schema      │
│  • default_schema: identity→ Hibernate uses identity schema               │
│  • flyway.schemas: identity→ Flyway manages identity schema               │
│                                                                              │
│  JWT:                                                                        │
│  ────                                                                       │
│  • secret: 256+ bit key for HMAC-SHA256 signing                            │
│  • access-token-expiry: 15 minutes (in milliseconds)                       │
│  • refresh-token-expiry: 7 days (in milliseconds)                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

### Step 4.6: Verify JWT Configuration

**Note:** PayFlow uses HMAC (HS256) for JWT signing, not RSA key pairs. This is simpler and sufficient for our architecture since the identity-service handles all token generation and validation.

The JWT secret is configured in `application.yml`:
```yaml
jwt:
  secret: your-256-bit-secret-key-for-jwt-token-signing-replace-in-production
  access-token-expiry: 900000      # 15 minutes
  refresh-token-expiry: 604800000  # 7 days
```

**Important:** In production, use a strong random secret (at least 256 bits / 32 characters) stored in environment variables or a secrets manager.

---

### Step 4.7: Create Security Configuration

**File: `identity-service/src/main/java/com/payflow/identity/config/SecurityConfig.java`**

```java
package com.payflow.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;


/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SECURITY CONFIGURATION
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Configures Spring Security for the Identity Service.
 * 
 * Key decisions:
 * ─────────────
 * • STATELESS session: No server-side sessions, JWT handles state
 * • CSRF disabled: Not needed for stateless REST APIs
 * • All endpoints public: Gateway handles authentication
 * 
 * Why let everything through?
 * ──────────────────────────
 * In microservices, authentication happens at the API Gateway level.
 * Internal service-to-service calls don't go through Gateway.
 * We trust requests that reach this service directly.
 * 
 * Network security (not service security) protects internal services:
 * • Services in private subnet (not internet accessible)
 * • Only Gateway is publicly accessible
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password encoder bean.
     * BCrypt is the recommended encoder for passwords.
     * 
     * Strength 12 = 2^12 = 4096 iterations (~250ms per hash, secure)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }


    /**
     * Security filter chain configuration.
     * 
     * This configures how Spring Security handles incoming requests.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ┌─────────────────────────────────────────────────────────────┐
            // │ CSRF (Cross-Site Request Forgery) Protection               │
            // │                                                              │
            // │ CSRF attacks trick users into making unwanted requests.    │
            // │ Protection: Server sends token, client must send it back.  │
            // │                                                              │
            // │ Why disable for REST APIs?                                  │
            // │ • CSRF relies on browser automatically sending cookies     │
            // │ • We use JWT in headers, not cookies                       │
            // │ • Attacker can't steal JWT from headers                    │
            // └─────────────────────────────────────────────────────────────┘
            .csrf(csrf -> csrf.disable())
            
            // ┌─────────────────────────────────────────────────────────────┐
            // │ SESSION MANAGEMENT                                          │
            // │                                                              │
            // │ STATELESS = Don't create HTTP sessions                     │
            // │                                                              │
            // │ Traditional web apps:                                       │
            // │ • User logs in → Server creates session → Returns cookie   │
            // │ • Every request: "Here's my session ID"                    │
            // │ • Server looks up session to know who you are              │
            // │                                                              │
            // │ JWT-based (stateless):                                      │
            // │ • User logs in → Server returns JWT                        │
            // │ • Every request: "Here's my JWT"                           │
            // │ • Server validates JWT (no lookup needed!)                 │
            // └─────────────────────────────────────────────────────────────┘
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // ┌─────────────────────────────────────────────────────────────┐
            // │ ENDPOINT PERMISSIONS                                        │
            // │                                                              │
            // │ /v1/auth/** = Public (login, register)                     │
            // │ /swagger-ui/** = Public (API docs)                         │
            // │ /actuator/** = Public (health checks)                      │
            // │ Everything else = Requires authentication                   │
            // └─────────────────────────────────────────────────────────────┘
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
            
        return http.build();
    }
}
```


---

### Step 4.8: Verify Database Schema Exists

The `payflow` database should already exist from Sprint 00 setup. Flyway will automatically create the `identity` schema when the service starts.

```powershell
# Verify payflow database exists
docker exec -it postgres psql -U payflow -d payflow -c "SELECT 1"
# Expected: 1

# After starting identity-service, verify identity schema was created
docker exec -it postgres psql -U payflow -d payflow -c "\dn"
# Expected: identity schema in the list
```

**Note:** PayFlow uses SCHEMA ISOLATION, not separate databases. All services share the `payflow` database but each has its own schema (identity, merchant, payment, settlement).

---

## 5. Verification

### 5.1 Build the Module

```powershell
# From project root
cd identity-service

# Clean and build
mvn clean package -DskipTests

# Expected output:
# [INFO] BUILD SUCCESS
```

### 5.2 Run the Application

```powershell
# Start Identity Service
mvn spring-boot:run
```

**Expected console output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
INFO  --- IdentityServiceApplication : Starting IdentityServiceApplication
INFO  --- Flyway : Migrating schema "public" to version "1 - create users table"
INFO  --- TomcatWebServer : Tomcat started on port 8081
INFO  --- IdentityServiceApplication : Started in X.XXX seconds
```

### 5.3 Test Endpoints

```powershell
# Test health check
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP"}

# Check Eureka registration
# Open browser: http://localhost:8761
# Should see: IDENTITY-SERVICE registered
```


---

## 6. File Structure

After completing this part, you should have:

```
identity-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/payflow/identity/
│   │   │       ├── IdentityServiceApplication.java
│   │   │       ├── config/
│   │   │       │   └── SecurityConfig.java
│   │   │       ├── controller/     (empty - created in Part 07)
│   │   │       ├── dto/            (empty - created in Part 05)
│   │   │       ├── model/          (empty - created in Part 05)
│   │   │       ├── repository/     (empty - created in Part 05)
│   │   │       └── service/        (empty - created in Part 06)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/       (empty - created in Part 05)
│   └── test/
│       └── java/com/payflow/identity/
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ Identity Service Purpose                                                │
│     • Central authentication service                                        │
│     • Issues JWT tokens                                                     │
│     • Manages user accounts                                                 │
│                                                                              │
│  ✅ Authentication vs Authorization                                         │
│     • AuthN = Who are you? (Identity Service)                              │
│     • AuthZ = What can you do? (Each service)                              │
│                                                                              │
│  ✅ JWT Token Structure                                                     │
│     • Header: Algorithm info                                                │
│     • Payload: User claims (NOT encrypted!)                                │
│     • Signature: Verification using keys                                   │
│                                                                              │
│  ✅ Access vs Refresh Tokens                                               │
│     • Access: Short-lived (15 min), for API calls                         │
│     • Refresh: Long-lived (7 days), to get new access tokens              │
│                                                                              │
│  ✅ Password Security                                                       │
│     • Never store plain text passwords                                     │
│     • BCrypt: Salted, slow, one-way hashing                               │
│                                                                              │
│  ✅ Spring Security Configuration                                          │
│     • Stateless session management                                         │
│     • CSRF disabled for REST APIs                                          │
│     • Password encoder bean                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 8. Q&A / Troubleshooting

### Q1: "JWT secret key" not configured error

**Cause:** Missing or invalid JWT secret in application.yml.

**Fix:**
```yaml
# Ensure jwt configuration exists in application.yml
jwt:
  secret: your-256-bit-secret-key-for-jwt-token-signing-replace-in-production
  access-token-expiry: 900000
  refresh-token-expiry: 604800000
```

The secret must be at least 256 bits (32+ characters) for HS256 algorithm.

### Q2: "Connection refused" to PostgreSQL

**Cause:** PostgreSQL not running or wrong credentials.

**Fix:**
```powershell
# Check PostgreSQL is running
docker ps | findstr postgres

# Start if not running
docker compose -f docker-compose-infra.yml up -d postgres

# Test connection to payflow database
docker exec -it postgres psql -U payflow -d payflow -c "SELECT 1"
```

### Q3: "Schema 'identity' does not exist"

**Cause:** Flyway hasn't created the schema yet, or service hasn't started.

**Fix:**
Flyway automatically creates the schema on first startup. Just start the service:
```powershell
cd identity-service
mvn spring-boot:run
```

To manually verify schema exists:
```powershell
docker exec -it postgres psql -U payflow -d payflow -c "\dn"
# Should show 'identity' schema in the list
```


### Q4: Why is Spring Security configured with specific endpoint permissions?

Security configuration allows:
- `/v1/auth/**` - Public (login/register endpoints)
- `/swagger-ui/**`, `/v3/api-docs/**` - Public (API documentation)
- `/actuator/**` - Public (health checks)
- Everything else requires authentication

This provides defense-in-depth even though API Gateway also validates tokens.

### Q5: Why schema isolation instead of separate databases?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  SCHEMA ISOLATION PATTERN (PayFlow uses this)                               │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Single 'payflow' database with multiple schemas:                   │   │
│  │                                                                      │   │
│  │  payflow database                                                   │   │
│  │    ├── identity schema   ← identity-service                        │   │
│  │    │     └── users table                                           │   │
│  │    ├── merchant schema   ← merchant-service                        │   │
│  │    │     ├── merchants table                                       │   │
│  │    │     └── api_keys table                                        │   │
│  │    ├── payment schema    ← payment-service                         │   │
│  │    │     └── transactions table                                    │   │
│  │    └── settlement schema ← settlement-service                      │   │
│  │          └── settlements table                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  BENEFITS OF SCHEMA ISOLATION:                                              │
│  ────────────────────────────                                               │
│  • Simpler backup/restore (single database)                                │
│  • Easier cross-service queries when needed                                │
│  • Lower operational complexity                                            │
│  • Same logical separation as separate databases                           │
│  • Each service only sees its own schema                                   │
│                                                                              │
│  URL format:                                                                │
│  jdbc:postgresql://localhost:5432/payflow?currentSchema=identity           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS TO EXPLORE                                 │
│                                                                              │
│  OAuth 2.0 / OpenID Connect                                                 │
│  ──────────────────────────                                                 │
│  Industry standard for authorization. Our JWT-based approach is            │
│  a simplified version. Production systems often use full OAuth 2.0.        │
│                                                                              │
│  Multi-Factor Authentication (MFA)                                          │
│  ────────────────────────────────                                           │
│  Add another layer: password + SMS code / authenticator app.               │
│  Can be added to the login flow later.                                     │
│                                                                              │
│  Single Sign-On (SSO)                                                       │
│  ────────────────────                                                       │
│  Allow users to login once, access multiple applications.                  │
│  Integration with Google, GitHub, corporate LDAP, etc.                     │
│                                                                              │
│  Token Blacklisting                                                         │
│  ─────────────────                                                         │
│  JWTs are stateless - can't be "invalidated" server-side.                 │
│  For logout, maintain a blacklist in Redis of revoked tokens.             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT'S NEXT                                               │
│                                                                              │
│  ✅ Part 04 COMPLETE: Identity Service Setup                                │
│                                                                              │
│  NEXT: Part 05 - Identity Database                                          │
│  ─────────────────────────────────                                          │
│  In Part 05, we'll create:                                                  │
│  • User entity (JPA)                                                        │
│  • Flyway migration scripts                                                 │
│  • User repository                                                          │
│  • DTOs for request/response                                               │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  IDENTITY SERVICE BUILD PROGRESS                                    │   │
│  │                                                                      │   │
│  │  Part 04: Setup ✅        - Project structure, config, security    │   │
│  │  Part 05: Database        - Entities, migrations, repositories      │   │
│  │  Part 06: JWT Auth        - Token generation and validation        │   │
│  │  Part 07: Controllers     - REST endpoints                          │   │
│  │  Part 08: Swagger         - API documentation                       │   │
│  │  Part 09: Testing         - Unit and integration tests             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Continue to: part-05-identity-database.md                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 04 Complete!** 🎉

You now have the Identity Service foundation with:
- Project structure and dependencies
- Spring Security configuration
- Database connection setup
- JWT configuration with HMAC secret (HS256)
