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
│  │    "alg": "RS256",    // Algorithm: RSA with SHA-256               │   │
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
│  │  RS256(                                                              │   │
│  │    base64(header) + "." + base64(payload),                          │   │
│  │    privateKey                                                        │   │
│  │  )                                                                   │   │
│  │                                                                      │   │
│  │  Only someone with private key can CREATE valid signature           │   │
│  │  Anyone with public key can VERIFY signature                        │   │
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

# Check PostgreSQL connection
docker exec -it postgres psql -U payflow -d payflow_identity -c "SELECT 1"
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
mkdir identity-service\src\main\java\com\payflow\identity\exception
mkdir identity-service\src\main\java\com\payflow\identity\repository
mkdir identity-service\src\main\java\com\payflow\identity\service
mkdir identity-service\src\main\resources
mkdir identity-service\src\main\resources\db\migration
mkdir identity-service\src\main\resources\keys
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
│   │   │       ├── entity/
│   │   │       ├── exception/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   │       ├── db/migration/
│   │       └── keys/
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
    <description>Authentication and user management service</description>


    <dependencies>
        
        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ SPRING BOOT WEB                                                 │
        │                                                                  │
        │ Provides:                                                        │
        │ • Embedded Tomcat server                                         │
        │ • REST controller support (@RestController)                     │
        │ • JSON serialization (Jackson)                                  │
        │ • Exception handling                                             │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ SPRING DATA JPA                                                 │
        │                                                                  │
        │ Provides:                                                        │
        │ • ORM (Object-Relational Mapping)                               │
        │ • Repository pattern (no SQL needed for basic CRUD)            │
        │ • Transaction management                                        │
        │ • Entity mapping (@Entity, @Column, etc.)                       │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ SPRING VALIDATION                                               │
        │                                                                  │
        │ Provides:                                                        │
        │ • @Valid annotation for request validation                      │
        │ • @NotNull, @Email, @Size, etc. constraints                    │
        │ • Automatic validation error responses                          │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>


        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ SPRING SECURITY                                                 │
        │                                                                  │
        │ Provides:                                                        │
        │ • Password encoding (BCrypt)                                    │
        │ • Security filters                                              │
        │ • CORS configuration                                            │
        │ • CSRF protection (disabled for REST APIs)                     │
        │                                                                  │
        │ Note: We configure it minimally here.                           │
        │ Full Spring Security is overkill for microservices -           │
        │ JWT validation happens at Gateway level.                        │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ FLYWAY                                                          │
        │                                                                  │
        │ Database migration tool. Provides:                               │
        │ • Version-controlled database schema                            │
        │ • Automatic migration on startup                                │
        │ • Team collaboration on DB changes                              │
        │                                                                  │
        │ Migration files: src/main/resources/db/migration/              │
        │ Naming: V1__description.sql, V2__description.sql               │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>


        <!-- JWT Libraries -->
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

        <!-- Config Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok (reduces boilerplate code) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Common Library -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
            <version>${project.version}</version>
        </dependency>


        <!-- Test Dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

---

### Step 4.4: Create Main Application Class

**File: `identity-service/src/main/java/com/payflow/identity/IdentityServiceApplication.java`**

```java
package com.payflow.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * IDENTITY SERVICE APPLICATION
 * Authentication and User Management
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * This service handles:
 * • User registration
 * • User authentication (login)
 * • JWT token generation
 * • Token refresh
 * • Password management
 * 
 * Key Endpoints:
 * ─────────────
 * POST /v1/auth/register   - Create new user account
 * POST /v1/auth/login      - Authenticate and get tokens
 * POST /v1/auth/refresh    - Get new access token using refresh token
 * POST /v1/auth/logout     - Invalidate refresh token
 */
@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```


---

### Step 4.5: Create application.yml

**File: `identity-service/src/main/resources/application.yml`**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# IDENTITY SERVICE CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8081

spring:
  application:
    name: identity-service
    
  # ─────────────────────────────────────────────────────────────────────────
  # CONFIG SERVER
  # ─────────────────────────────────────────────────────────────────────────
  config:
    import: optional:configserver:http://localhost:8888

  # ─────────────────────────────────────────────────────────────────────────
  # DATABASE CONFIGURATION
  # ─────────────────────────────────────────────────────────────────────────
  datasource:
    # ┌─────────────────────────────────────────────────────────────────────┐
    # │ JDBC URL Format:                                                    │
    # │ jdbc:postgresql://host:port/database                               │
    # │                                                                     │
    # │ payflow_identity = Separate database for identity service          │
    # │ Each microservice should have its own database (database per service)│
    # └─────────────────────────────────────────────────────────────────────┘
    url: jdbc:postgresql://localhost:5432/payflow_identity
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver
    
  # ─────────────────────────────────────────────────────────────────────────
  # JPA CONFIGURATION
  # ─────────────────────────────────────────────────────────────────────────
  jpa:
    hibernate:
      # ┌─────────────────────────────────────────────────────────────────┐
      # │ DDL-AUTO OPTIONS:                                               │
      # │ • none: Don't touch schema (production)                        │
      # │ • validate: Validate schema matches entities (recommended)     │
      # │ • update: Update schema (dangerous in production!)             │
      # │ • create: Drop and recreate (only for testing!)               │
      # │ • create-drop: Create on start, drop on stop                  │
      # │                                                                 │
      # │ We use 'validate' + Flyway for schema management              │
      # └─────────────────────────────────────────────────────────────────┘
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: true  # Set to false in production


  # ─────────────────────────────────────────────────────────────────────────
  # FLYWAY CONFIGURATION
  # ─────────────────────────────────────────────────────────────────────────
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

# ─────────────────────────────────────────────────────────────────────────────
# EUREKA CLIENT
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# ─────────────────────────────────────────────────────────────────────────────
# JWT CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────
jwt:
  # ┌─────────────────────────────────────────────────────────────────────────┐
  # │ PRIVATE KEY: Used to SIGN tokens (only identity-service has this)     │
  # │ PUBLIC KEY: Used to VERIFY tokens (gateway and other services)        │
  # └─────────────────────────────────────────────────────────────────────────┘
  private-key-path: classpath:keys/private.pem
  public-key-path: classpath:keys/public.pem
  
  # Token expiration times
  access-token-expiration: 900000      # 15 minutes in milliseconds
  refresh-token-expiration: 604800000  # 7 days in milliseconds

# ─────────────────────────────────────────────────────────────────────────────
# ACTUATOR
# ─────────────────────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```


---

### Step 4.6: Copy RSA Keys

Copy the RSA keys from api-gateway (or generate new ones):

```powershell
# Option 1: Copy from api-gateway
copy api-gateway\src\main\resources\keys\*.pem identity-service\src\main\resources\keys\

# Option 2: Generate new keys (if not done in Part 03)
openssl genrsa -out identity-service\src\main\resources\keys\private.pem 2048
openssl rsa -in identity-service\src\main\resources\keys\private.pem -pubout -out identity-service\src\main\resources\keys\public.pem
```

**Important:** In production, use the SAME key pair across all services. The private key should be kept secret and only accessible to the identity-service.

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
     * Strength 10 = 2^10 = 1024 iterations (good balance of security/speed)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }


    /**
     * Security filter chain configuration.
     * 
     * This configures how Spring Security handles incoming requests.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
            
            // All requests allowed (Gateway handles auth)
            .authorizeHttpRequests(auth -> 
                auth.anyRequest().permitAll()
            );
            
        return http.build();
    }
}
```


---

### Step 4.8: Create Database for Identity Service

```powershell
# Connect to PostgreSQL and create database
docker exec -it postgres psql -U payflow -d postgres

# Inside psql shell:
CREATE DATABASE payflow_identity;
\q
```

Or using a single command:
```powershell
docker exec -it postgres psql -U payflow -d postgres -c "CREATE DATABASE payflow_identity;"
```

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
│   │   │       ├── entity/         (empty - created in Part 05)
│   │   │       ├── exception/      (empty - created in Part 07)
│   │   │       ├── repository/     (empty - created in Part 05)
│   │   │       └── service/        (empty - created in Part 06)
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── db/migration/       (empty - created in Part 05)
│   │       └── keys/
│   │           ├── private.pem
│   │           └── public.pem
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

### Q1: "Could not load JWT private key" error

**Cause:** Key file not found or wrong format.

**Fix:**
```powershell
# Check files exist
dir identity-service\src\main\resources\keys\

# Verify key format (should start with -----BEGIN...)
type identity-service\src\main\resources\keys\private.pem

# Regenerate if corrupted
openssl genrsa -out identity-service\src\main\resources\keys\private.pem 2048
openssl rsa -in identity-service\src\main\resources\keys\private.pem -pubout -out identity-service\src\main\resources\keys\public.pem
```

### Q2: "Connection refused" to PostgreSQL

**Cause:** PostgreSQL not running or wrong credentials.

**Fix:**
```powershell
# Check PostgreSQL is running
docker ps | findstr postgres

# Start if not running
docker compose -f docker-compose-infra.yml up -d postgres

# Test connection
docker exec -it postgres psql -U payflow -d payflow_identity -c "SELECT 1"
```

### Q3: "Database 'payflow_identity' does not exist"

**Cause:** Database not created yet.

**Fix:**
```powershell
docker exec -it postgres psql -U payflow -d postgres -c "CREATE DATABASE payflow_identity;"
```


### Q4: Why is Spring Security letting everything through?

**This is intentional.** In microservices:
1. API Gateway validates JWT tokens (already done in Part 03)
2. Internal services trust requests that reach them
3. Network security (private subnet) protects internal services

If you need service-level security, add JWT validation here too.

### Q5: Why separate databases per service?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  DATABASE PER SERVICE PATTERN                                               │
│                                                                              │
│  Shared database:              Database per service:                        │
│  ────────────────              ─────────────────────                        │
│                                                                              │
│  All services ─►  postgres     identity-service ─► payflow_identity        │
│       │                        merchant-service ─► payflow_merchant        │
│       └── Tight coupling       payment-service  ─► payflow_payment         │
│       └── Schema conflicts                                                  │
│       └── Single point of      • Loose coupling                            │
│           failure              • Independent scaling                        │
│                                • Team autonomy                              │
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
- RSA key pair for JWT
