# Sprint 1, Part 03: Identity Service

**Duration:** 4-5 hours  
**Prerequisites:** Parts 01-02 completed, PostgreSQL running

---

## 1. What We're Building

The Identity Service handles all authentication and user management:

| Feature | Endpoint | Purpose |
|---------|----------|---------|
| Register | POST /v1/auth/register | Create new user account |
| Login | POST /v1/auth/login | Authenticate and get JWT |
| Get Profile | GET /v1/auth/me | Get current user info |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     IDENTITY SERVICE OVERVIEW                                │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      IDENTITY SERVICE                                │   │
│  │                        (Port 8081)                                   │   │
│  │                                                                      │   │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐      │   │
│  │  │ AuthController│───►│ AuthService  │───►│ UserRepository   │      │   │
│  │  │              │    │              │    │                  │      │   │
│  │  │ POST /register│    │ • Validate   │    │ • findByEmail    │      │   │
│  │  │ POST /login  │    │ • Hash pwd   │    │ • save           │      │   │
│  │  │ GET /me      │    │ • Create JWT │    │ • existsByEmail  │      │   │
│  │  └──────────────┘    └──────────────┘    └──────────────────┘      │   │
│  │                            │                      │                 │   │
│  │                            │                      │                 │   │
│  │                    ┌───────▼───────┐      ┌───────▼───────┐        │   │
│  │                    │JwtTokenProvider│      │  PostgreSQL   │        │   │
│  │                    │               │      │  (identity    │        │   │
│  │                    │ • Generate    │      │   schema)     │        │   │
│  │                    │ • Sign RS256  │      │               │        │   │
│  │                    └───────────────┘      └───────────────┘        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Registration Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REGISTRATION FLOW                                    │
│                                                                              │
│  Client                    Identity Service                   Database      │
│    │                            │                               │           │
│    │ POST /v1/auth/register     │                               │           │
│    │ {email, password, name}    │                               │           │
│    │ ──────────────────────────►│                               │           │
│    │                            │                               │           │
│    │                            │ 1. Validate input             │           │
│    │                            │    • Email format             │           │
│    │                            │    • Password strength        │           │
│    │                            │    • Name not empty           │           │
│    │                            │                               │           │
│    │                            │ 2. Check email exists         │           │
│    │                            │ ─────────────────────────────►│           │
│    │                            │ ◄─────────────────────────────│           │
│    │                            │    (false = available)        │           │
│    │                            │                               │           │
│    │                            │ 3. Hash password              │           │
│    │                            │    "Pass123" → "$2a$12$..."   │           │
│    │                            │    (BCrypt, cost 12)          │           │
│    │                            │                               │           │
│    │                            │ 4. Save user                  │           │
│    │                            │ ─────────────────────────────►│           │
│    │                            │ ◄─────────────────────────────│           │
│    │                            │    (user with ID)             │           │
│    │                            │                               │           │
│    │                            │ 5. Generate JWT               │           │
│    │                            │    Sign with PRIVATE key      │           │
│    │                            │                               │           │
│    │ {token, user}              │                               │           │
│    │ ◄──────────────────────────│                               │           │
│    │                            │                               │           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Login Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LOGIN FLOW                                        │
│                                                                              │
│  Client                    Identity Service                   Database      │
│    │                            │                               │           │
│    │ POST /v1/auth/login        │                               │           │
│    │ {email, password}          │                               │           │
│    │ ──────────────────────────►│                               │           │
│    │                            │                               │           │
│    │                            │ 1. Find user by email         │           │
│    │                            │ ─────────────────────────────►│           │
│    │                            │ ◄─────────────────────────────│           │
│    │                            │    (user with hashed pwd)     │           │
│    │                            │                               │           │
│    │                            │ 2. Verify password            │           │
│    │                            │    BCrypt.matches(            │           │
│    │                            │      "Pass123",               │           │
│    │                            │      "$2a$12$..."             │           │
│    │                            │    )                          │           │
│    │                            │                               │           │
│    │                            │ 3. If match: Generate JWT     │           │
│    │                            │    If no match: 401 error     │           │
│    │                            │                               │           │
│    │ {token, user}              │                               │           │
│    │ ◄──────────────────────────│                               │           │
│    │                            │                               │           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 JWT Generation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          JWT TOKEN GENERATION                                │
│                                                                              │
│  User logged in successfully, now we create a JWT:                          │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  JwtTokenProvider.generateToken(user)                                │   │
│  │                                                                      │   │
│  │  1. Create header:                                                   │   │
│  │     {                                                                │   │
│  │       "alg": "RS256",    // Algorithm: RSA + SHA-256                 │   │
│  │       "typ": "JWT"       // Type: JSON Web Token                     │   │
│  │     }                                                                │   │
│  │                                                                      │   │
│  │  2. Create payload (claims):                                         │   │
│  │     {                                                                │   │
│  │       "sub": "550e8400-e29b-...",  // Subject (user ID)              │   │
│  │       "email": "user@example.com", // Custom claim                   │   │
│  │       "role": "MERCHANT",          // Custom claim                   │   │
│  │       "iat": 1722771600,           // Issued at (Unix timestamp)     │   │
│  │       "exp": 1722858000            // Expires at (iat + 24h)         │   │
│  │     }                                                                │   │
│  │                                                                      │   │
│  │  3. Sign with PRIVATE key:                                           │   │
│  │     signature = RSA-SHA256(                                          │   │
│  │       base64(header) + "." + base64(payload),                        │   │
│  │       privateKey                                                      │   │
│  │     )                                                                │   │
│  │                                                                      │   │
│  │  4. Combine:                                                         │   │
│  │     token = base64(header) + "." + base64(payload) + "." + signature│   │
│  │                                                                      │   │
│  │  Result:                                                             │   │
│  │  eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI1NTBlODQwMC4u...   │   │
│  │  │                                  │                            │   │   │
│  │  └─ header                          └─ payload                   │   │   │
│  │                                                            signature │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Verify before starting:

```powershell
# Check PostgreSQL is running
docker ps | findstr postgres
# Should show postgres container running

# Check services are running
curl http://localhost:8761/actuator/health   # Eureka
curl http://localhost:8888/actuator/health   # Config Server
curl http://localhost:8080/actuator/health   # API Gateway
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

Add identity-service module to parent `pom.xml`:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>
    <module>api-gateway</module>
    <module>identity-service</module>    <!-- ADD THIS -->
</modules>
```

---

### Step 4.2: Create Identity Service Module

**Create folder structure:**

```powershell
mkdir identity-service
mkdir identity-service\src\main\java\com\payflow\identity
mkdir identity-service\src\main\java\com\payflow\identity\controller
mkdir identity-service\src\main\java\com\payflow\identity\service
mkdir identity-service\src\main\java\com\payflow\identity\service\impl
mkdir identity-service\src\main\java\com\payflow\identity\repository
mkdir identity-service\src\main\java\com\payflow\identity\entity
mkdir identity-service\src\main\java\com\payflow\identity\dto
mkdir identity-service\src\main\java\com\payflow\identity\security
mkdir identity-service\src\main\java\com\payflow\identity\config
mkdir identity-service\src\main\resources
mkdir identity-service\src\main\resources\keys
mkdir identity-service\src\test\java\com\payflow\identity
```


---

### Step 4.3: Create identity-service/pom.xml

Create `identity-service/pom.xml`:

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
        Spring Web (MVC)
        Unlike Gateway (WebFlux), this uses traditional Spring MVC
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 
        Spring Data JPA
        For database operations with PostgreSQL
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- 
        PostgreSQL Driver
        -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- 
        Spring Security
        For password encoding (BCrypt)
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- 
        Eureka Client
        Register with Service Registry
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- 
        Config Client
        Fetch config from Config Server
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>

        <!-- 
        JJWT - JWT Library
        For creating and signing JWTs
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
        Validation
        For @Valid, @NotBlank, @Email annotations
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Actuator for health checks -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Common library (DTOs, exceptions) -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test dependencies -->
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

### Step 4.4: Create User Entity

Create `identity-service/src/main/java/com/payflow/identity/entity/User.java`:

```java
package com.payflow.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Entity
 * 
 * Represents a user in the PayFlow system.
 * Stored in the 'identity' schema, 'users' table.
 * 
 * Table structure:
 * ┌──────────────┬──────────────┬────────────────────────────────────┐
 * │ Column       │ Type         │ Description                        │
 * ├──────────────┼──────────────┼────────────────────────────────────┤
 * │ id           │ UUID         │ Primary key (auto-generated)       │
 * │ email        │ VARCHAR(255) │ Unique, used for login             │
 * │ password     │ VARCHAR(255) │ BCrypt hashed password             │
 * │ full_name    │ VARCHAR(100) │ User's display name                │
 * │ role         │ VARCHAR(20)  │ MERCHANT or ADMIN                  │
 * │ status       │ VARCHAR(20)  │ ACTIVE, INACTIVE, or LOCKED        │
 * │ created_at   │ TIMESTAMP    │ When user was created              │
 * │ updated_at   │ TIMESTAMP    │ When user was last updated         │
 * └──────────────┴──────────────┴────────────────────────────────────┘
 */
@Entity
@Table(
    name = "users",
    schema = "identity",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "email", name = "uk_users_email")
    },
    indexes = {
        @Index(columnList = "email", name = "idx_users_email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Primary key - UUID
     * 
     * Why UUID instead of auto-increment?
     * - Globally unique (no conflicts across databases)
     * - Can generate on client side (no DB roundtrip)
     * - Harder to guess/enumerate (security)
     * - Better for distributed systems
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Email - used for login
     * Must be unique across all users
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Password - BCrypt hashed
     * NEVER store plain text passwords!
     * 
     * BCrypt output format: $2a$12$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
     * - $2a$ = BCrypt algorithm version
     * - 12 = Cost factor (2^12 = 4096 iterations)
     * - Rest = Salt + Hash
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * Full name for display
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * User role
     * Controls what the user can do in the system
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.MERCHANT;

    /**
     * Account status
     * Used to disable/lock accounts
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Timestamps
     * Automatically managed by Hibernate
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * User roles enum
     */
    public enum UserRole {
        MERCHANT,  // Regular merchant user
        ADMIN      // System administrator
    }

    /**
     * User status enum
     */
    public enum UserStatus {
        ACTIVE,    // Can login and use system
        INACTIVE,  // Account disabled
        LOCKED     // Locked due to security (e.g., too many failed logins)
    }
}
```

---

### Step 4.5: Create UserRepository

Create `identity-service/src/main/java/com/payflow/identity/repository/UserRepository.java`:

```java
package com.payflow.identity.repository;

import com.payflow.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * User Repository
 * 
 * Spring Data JPA repository for User entity.
 * Spring automatically implements these methods based on naming conventions.
 * 
 * How Spring Data JPA works:
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Method Name                  │  Generated SQL                              │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │  findByEmail(email)           │  SELECT * FROM users WHERE email = ?        │
 * │  existsByEmail(email)         │  SELECT COUNT(*) > 0 FROM users WHERE ...   │
 * │  findById(id)                 │  SELECT * FROM users WHERE id = ?           │
 * │  save(user)                   │  INSERT/UPDATE based on ID                  │
 * │  delete(user)                 │  DELETE FROM users WHERE id = ?             │
 * └─────────────────────────────────────────────────────────────────────────────┘
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email
     * Used during login to retrieve user for password verification
     * 
     * @param email User's email address
     * @return Optional containing user if found, empty if not
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if email already exists
     * Used during registration to prevent duplicate emails
     * 
     * @param email Email to check
     * @return true if email exists, false otherwise
     */
    boolean existsByEmail(String email);
}
```

---

### Step 4.6: Create DTOs

Create `identity-service/src/main/java/com/payflow/identity/dto/RegisterRequest.java`:

```java
package com.payflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration Request DTO
 * 
 * Data sent by client when registering a new account.
 * Validation annotations ensure data quality before processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * Email address
     * - Must be valid email format
     * - Will be used for login
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /**
     * Password
     * - Minimum 8 characters
     * - Must contain: uppercase, lowercase, number
     * 
     * Pattern explanation:
     * ^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$
     * ^           = Start of string
     * (?=.*[a-z]) = Must contain lowercase
     * (?=.*[A-Z]) = Must contain uppercase
     * (?=.*\d)    = Must contain digit
     * .{8,}       = At least 8 characters
     * $           = End of string
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
        message = "Password must contain uppercase, lowercase, and number"
    )
    private String password;

    /**
     * Full name for display
     */
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    private String fullName;
}
```

Create `identity-service/src/main/java/com/payflow/identity/dto/LoginRequest.java`:

```java
package com.payflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login Request DTO
 * 
 * Data sent by client when logging in.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

Create `identity-service/src/main/java/com/payflow/identity/dto/AuthResponse.java`:

```java
package com.payflow.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication Response DTO
 * 
 * Returned after successful login or registration.
 * Contains JWT token and user info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * JWT access token
     * Client should store this and send in Authorization header
     */
    private String accessToken;

    /**
     * Token type (always "Bearer")
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Token expiration in seconds
     */
    private long expiresIn;

    /**
     * User information
     */
    private UserDto user;
}
```

Create `identity-service/src/main/java/com/payflow/identity/dto/UserDto.java`:

```java
package com.payflow.identity.dto;

import com.payflow.identity.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User DTO
 * 
 * User information returned to clients.
 * Does NOT include password!
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private LocalDateTime createdAt;

    /**
     * Convert User entity to DTO
     * This is a static factory method pattern
     */
    public static UserDto fromEntity(User user) {
        return UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
```


---

### Step 4.7: Create JwtTokenProvider

Create `identity-service/src/main/java/com/payflow/identity/security/JwtTokenProvider.java`:

```java
package com.payflow.identity.security;

import com.payflow.identity.entity.User;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * JWT Token Provider
 * 
 * Responsible for CREATING JWT tokens.
 * Uses RSA PRIVATE key to sign tokens.
 * 
 * IMPORTANT: Only this service has the private key!
 * - Private key: Used to SIGN (create) tokens
 * - Public key: Used to VERIFY tokens (in Gateway)
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.private-key-path:classpath:keys/private.pem}")
    private Resource privateKeyResource;

    @Value("${jwt.expiration:86400}")  // Default: 24 hours in seconds
    private long jwtExpiration;

    @Value("${jwt.issuer:payflow}")
    private String jwtIssuer;

    private PrivateKey privateKey;

    /**
     * Load private key on startup
     */
    @PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey();
            log.info("✓ JWT private key loaded successfully");
        } catch (Exception e) {
            log.error("✗ Failed to load JWT private key: {}", e.getMessage());
            throw new RuntimeException("Could not load private key", e);
        }
    }

    /**
     * Load RSA private key from PEM file
     */
    private PrivateKey loadPrivateKey() throws Exception {
        String keyContent = new String(
            Files.readAllBytes(privateKeyResource.getFile().toPath()),
            StandardCharsets.UTF_8
        );

        // Handle both PKCS#1 and PKCS#8 formats
        keyContent = keyContent
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    /**
     * Generate JWT token for user
     * 
     * Token structure:
     * {
     *   "sub": "user-uuid",           // Subject (user ID)
     *   "email": "user@example.com",  // Custom claim
     *   "role": "MERCHANT",           // Custom claim
     *   "iss": "payflow",             // Issuer
     *   "iat": 1234567890,            // Issued at
     *   "exp": 1234654290             // Expiration
     * }
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (jwtExpiration * 1000));

        return Jwts.builder()
            // Standard claims
            .subject(user.getId().toString())  // User ID
            .issuer(jwtIssuer)                 // "payflow"
            .issuedAt(now)                     // Current time
            .expiration(expiryDate)            // Expiry time
            
            // Custom claims
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            
            // Sign with private key (RS256)
            .signWith(privateKey)
            
            // Build the token string
            .compact();
    }

    /**
     * Get token expiration in seconds
     */
    public long getExpirationSeconds() {
        return jwtExpiration;
    }
}
```

---

### Step 4.8: Create AuthService

Create `identity-service/src/main/java/com/payflow/identity/service/AuthService.java`:

```java
package com.payflow.identity.service;

import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.dto.UserDto;

import java.util.UUID;

/**
 * Authentication Service Interface
 * 
 * Defines the contract for authentication operations.
 * Implementation handles the actual business logic.
 */
public interface AuthService {

    /**
     * Register a new user
     * 
     * @param request Registration data (email, password, name)
     * @return AuthResponse with JWT token and user info
     * @throws EmailAlreadyExistsException if email is taken
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Login existing user
     * 
     * @param request Login credentials (email, password)
     * @return AuthResponse with JWT token and user info
     * @throws InvalidCredentialsException if credentials are wrong
     */
    AuthResponse login(LoginRequest request);

    /**
     * Get current user info
     * 
     * @param userId User ID from JWT
     * @return User information
     * @throws UserNotFoundException if user doesn't exist
     */
    UserDto getCurrentUser(UUID userId);
}
```

Create `identity-service/src/main/java/com/payflow/identity/service/impl/AuthServiceImpl.java`:

```java
package com.payflow.identity.service.impl;

import com.payflow.common.exception.PayflowException;
import com.payflow.identity.dto.*;
import com.payflow.identity.entity.User;
import com.payflow.identity.repository.UserRepository;
import com.payflow.identity.security.JwtTokenProvider;
import com.payflow.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authentication Service Implementation
 * 
 * Handles all authentication business logic:
 * - User registration with password hashing
 * - User login with password verification
 * - JWT token generation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register new user
     * 
     * Flow:
     * 1. Check if email already exists
     * 2. Hash password with BCrypt
     * 3. Create and save user
     * 4. Generate JWT token
     * 5. Return token + user info
     */
    @Override
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        // ═══════════════════════════════════════════════════════════════════
        // Step 1: Check if email already exists
        // ═══════════════════════════════════════════════════════════════════
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email already exists - {}", request.getEmail());
            throw new PayflowException(
                "EMAIL_EXISTS",
                "Email already registered",
                HttpStatus.CONFLICT
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // Step 2: Hash password
        // BCrypt automatically generates salt and includes it in the hash
        // ═══════════════════════════════════════════════════════════════════
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // ═══════════════════════════════════════════════════════════════════
        // Step 3: Create and save user
        // ═══════════════════════════════════════════════════════════════════
        User user = User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .password(hashedPassword)
            .fullName(request.getFullName().trim())
            .role(User.UserRole.MERCHANT)
            .status(User.UserStatus.ACTIVE)
            .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {} (ID: {})", user.getEmail(), user.getId());

        // ═══════════════════════════════════════════════════════════════════
        // Step 4: Generate JWT token
        // ═══════════════════════════════════════════════════════════════════
        String token = jwtTokenProvider.generateToken(user);

        // ═══════════════════════════════════════════════════════════════════
        // Step 5: Return response
        // ═══════════════════════════════════════════════════════════════════
        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationSeconds())
            .user(UserDto.fromEntity(user))
            .build();
    }

    /**
     * Login user
     * 
     * Flow:
     * 1. Find user by email
     * 2. Verify password
     * 3. Check account status
     * 4. Generate JWT token
     * 5. Return token + user info
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for: {}", request.getEmail());

        // ═══════════════════════════════════════════════════════════════════
        // Step 1: Find user by email
        // ═══════════════════════════════════════════════════════════════════
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
            .orElseThrow(() -> {
                log.warn("Login failed: User not found - {}", request.getEmail());
                return new PayflowException(
                    "INVALID_CREDENTIALS",
                    "Invalid email or password",
                    HttpStatus.UNAUTHORIZED
                );
            });

        // ═══════════════════════════════════════════════════════════════════
        // Step 2: Verify password
        // BCrypt.matches compares plain password with hash
        // ═══════════════════════════════════════════════════════════════════
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: Invalid password for - {}", request.getEmail());
            throw new PayflowException(
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                HttpStatus.UNAUTHORIZED
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // Step 3: Check account status
        // ═══════════════════════════════════════════════════════════════════
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            log.warn("Login failed: Account not active - {} (status: {})", 
                request.getEmail(), user.getStatus());
            throw new PayflowException(
                "ACCOUNT_INACTIVE",
                "Account is not active. Please contact support.",
                HttpStatus.FORBIDDEN
            );
        }

        // ═══════════════════════════════════════════════════════════════════
        // Step 4: Generate JWT token
        // ═══════════════════════════════════════════════════════════════════
        String token = jwtTokenProvider.generateToken(user);
        log.info("Login successful for: {}", request.getEmail());

        // ═══════════════════════════════════════════════════════════════════
        // Step 5: Return response
        // ═══════════════════════════════════════════════════════════════════
        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationSeconds())
            .user(UserDto.fromEntity(user))
            .build();
    }

    /**
     * Get current user info
     */
    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new PayflowException(
                "USER_NOT_FOUND",
                "User not found",
                HttpStatus.NOT_FOUND
            ));

        return UserDto.fromEntity(user);
    }
}
```

---

### Step 4.9: Create AuthController

Create `identity-service/src/main/java/com/payflow/identity/controller/AuthController.java`:

```java
package com.payflow.identity.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.identity.dto.*;
import com.payflow.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authentication Controller
 * 
 * REST API endpoints for authentication:
 * - POST /v1/auth/register - Register new user
 * - POST /v1/auth/login    - Login user
 * - GET  /v1/auth/me       - Get current user
 * 
 * Note: /v1/auth/** routes are PUBLIC (no JWT required)
 * except /v1/auth/me which requires JWT
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Register new user
     * 
     * POST /v1/auth/register
     * 
     * Request Body:
     * {
     *   "email": "user@example.com",
     *   "password": "SecurePass123",
     *   "fullName": "John Doe"
     * }
     * 
     * Response (201 Created):
     * {
     *   "success": true,
     *   "data": {
     *     "accessToken": "eyJhbG...",
     *     "tokenType": "Bearer",
     *     "expiresIn": 86400,
     *     "user": { ... }
     *   }
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        
        log.info("POST /v1/auth/register - email: {}", request.getEmail());
        
        AuthResponse response = authService.register(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * Login user
     * 
     * POST /v1/auth/login
     * 
     * Request Body:
     * {
     *   "email": "user@example.com",
     *   "password": "SecurePass123"
     * }
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": {
     *     "accessToken": "eyJhbG...",
     *     "tokenType": "Bearer",
     *     "expiresIn": 86400,
     *     "user": { ... }
     *   }
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        
        log.info("POST /v1/auth/login - email: {}", request.getEmail());
        
        AuthResponse response = authService.login(request);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get current user
     * 
     * GET /v1/auth/me
     * 
     * Requires: Authorization: Bearer <token>
     * 
     * The API Gateway validates the token and adds X-User-Id header.
     * We read the user ID from that header.
     * 
     * Response (200 OK):
     * {
     *   "success": true,
     *   "data": {
     *     "id": "550e8400-...",
     *     "email": "user@example.com",
     *     "fullName": "John Doe",
     *     "role": "MERCHANT",
     *     "status": "ACTIVE",
     *     "createdAt": "2026-08-04T10:30:00"
     *   }
     * }
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(
            @RequestHeader("X-User-Id") String userIdHeader) {
        
        log.info("GET /v1/auth/me - userId: {}", userIdHeader);
        
        UUID userId = UUID.fromString(userIdHeader);
        UserDto user = authService.getCurrentUser(userId);
        
        return ResponseEntity.ok(ApiResponse.success(user));
    }
}
```


---

### Step 4.10: Create SecurityConfig

Create `identity-service/src/main/java/com/payflow/identity/config/SecurityConfig.java`:

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
 * Spring Security Configuration
 * 
 * Configures:
 * - Password encoder (BCrypt)
 * - HTTP security (stateless, permit all - Gateway handles auth)
 * 
 * Why permit all?
 * - API Gateway validates JWT tokens
 * - This service trusts requests from Gateway (internal network)
 * - No need to re-validate here
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password Encoder Bean
     * 
     * BCrypt is the industry standard for password hashing:
     * - Includes salt automatically
     * - Configurable cost factor (default 10, we use 12)
     * - Resistant to rainbow table attacks
     * 
     * Cost factor 12 means: 2^12 = 4096 iterations
     * Higher = more secure but slower
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Security Filter Chain
     * 
     * Configures HTTP security for this service.
     * We permit all requests because Gateway handles authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (not needed for stateless API)
            .csrf(csrf -> csrf.disable())
            
            // Stateless session (no cookies)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Permit all requests (Gateway handles auth)
            .authorizeHttpRequests(auth -> 
                auth.anyRequest().permitAll()
            );
        
        return http.build();
    }
}
```

---

### Step 4.11: Create Application Main Class

Create `identity-service/src/main/java/com/payflow/identity/IdentityServiceApplication.java`:

```java
package com.payflow.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Identity Service Application
 * 
 * Handles authentication and user management:
 * - User registration
 * - User login
 * - JWT token generation
 * - User profile retrieval
 */
@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
```

---

### Step 4.12: Create application.yml

Create `identity-service/src/main/resources/application.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# Identity Service Configuration
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8081

spring:
  application:
    name: identity-service
    
  # ─────────────────────────────────────────────────────────────────────────
  # Config Server (fetch additional config)
  # ─────────────────────────────────────────────────────────────────────────
  config:
    import: optional:configserver:http://localhost:8888
    
  # ─────────────────────────────────────────────────────────────────────────
  # Database Configuration
  # ─────────────────────────────────────────────────────────────────────────
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow
    username: payflow
    password: payflow123
    driver-class-name: org.postgresql.Driver
    
  jpa:
    hibernate:
      ddl-auto: update  # Auto-create/update tables (dev only!)
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: identity  # Use 'identity' schema

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
  private-key-path: classpath:keys/private.pem
  expiration: 86400  # 24 hours in seconds
  issuer: payflow

# ─────────────────────────────────────────────────────────────────────────────
# Actuator
# ─────────────────────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info

# ─────────────────────────────────────────────────────────────────────────────
# Logging
# ─────────────────────────────────────────────────────────────────────────────
logging:
  level:
    com.payflow.identity: DEBUG
    org.springframework.security: DEBUG
```

---

### Step 4.13: Copy Private Key

Make sure the RSA private key is in place:

```powershell
# Check if private key exists
dir identity-service\src\main\resources\keys\

# If not, generate or copy from api-gateway (where we generated it earlier)
# The private key should already be here from Part 02
```

If you need to regenerate:

```powershell
cd identity-service\src\main\resources\keys

# Generate private key
openssl genrsa -out private.pem 2048

# Extract public key (copy to api-gateway)
openssl rsa -in private.pem -pubout -out public.pem

# Copy public key to gateway
copy public.pem ..\..\..\..\..\..\api-gateway\src\main\resources\keys\
```

---

## 5. Verification

### 5.1 Build and Run

```powershell
# Make sure infrastructure is running
docker ps | findstr postgres

# Build Identity Service
cd identity-service
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected output:**
```
Started IdentityServiceApplication in X.XXX seconds
✓ JWT private key loaded successfully
```

### 5.2 Test Registration

```powershell
# Register a new user
curl -X POST http://localhost:8080/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"test@example.com","password":"Test1234","fullName":"Test User"}'
```

**Expected response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "test@example.com",
      "fullName": "Test User",
      "role": "MERCHANT",
      "status": "ACTIVE"
    }
  }
}
```

### 5.3 Test Login

```powershell
curl -X POST http://localhost:8080/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"test@example.com","password":"Test1234"}'
```

### 5.4 Test Get Profile

```powershell
# Use the token from login response
$token = "eyJhbGciOiJSUzI1NiJ9..."

curl http://localhost:8080/v1/auth/me `
  -H "Authorization: Bearer $token"
```

### 5.5 Verification Checklist

| Check | Test | Expected |
|-------|------|----------|
| Service running | http://localhost:8081/actuator/health | UP |
| Registered with Eureka | http://localhost:8761 | IDENTITY-SERVICE listed |
| Registration works | POST /v1/auth/register | 201 Created with token |
| Duplicate email | Register same email twice | 409 Conflict |
| Login works | POST /v1/auth/login | 200 OK with token |
| Wrong password | Login with bad password | 401 Unauthorized |
| Get profile | GET /v1/auth/me with token | 200 OK with user data |

---

## 6. File Structure After This Part

```
identity-service/
├── pom.xml
├── src/main/
│   ├── java/com/payflow/identity/
│   │   ├── IdentityServiceApplication.java
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── dto/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── AuthResponse.java
│   │   │   └── UserDto.java
│   │   ├── entity/
│   │   │   └── User.java
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   ├── security/
│   │   │   └── JwtTokenProvider.java
│   │   └── service/
│   │       ├── AuthService.java
│   │       └── impl/
│   │           └── AuthServiceImpl.java
│   └── resources/
│       ├── application.yml
│       └── keys/
│           └── private.pem
└── src/test/java/
    └── ... (tests)
```

---

## 7. Key Takeaways

### Authentication Concepts

| Concept | What It Does | Why It Matters |
|---------|--------------|----------------|
| Password Hashing | Convert password to hash | Never store plain passwords |
| BCrypt | Industry standard hasher | Includes salt, configurable cost |
| JWT | Stateless auth token | No session storage needed |
| RS256 | Asymmetric signing | Private key stays in one service |

### Security Best Practices

| Practice | Why |
|----------|-----|
| Hash passwords | Protect user data if DB leaked |
| Use BCrypt with cost 12+ | Slow brute force attacks |
| Validate input | Prevent injection, bad data |
| Return generic errors | Don't reveal if email exists (for login) |
| Log security events | Audit trail for incidents |

---

## 8. Common Issues & Solutions

### Issue 1: "Table 'identity.users' doesn't exist"

**Solution:**
```sql
-- Connect to PostgreSQL and create schema
CREATE SCHEMA IF NOT EXISTS identity;
```

Or let Hibernate create it by adding to application.yml:
```yaml
spring.jpa.hibernate.ddl-auto: create
```

### Issue 2: Private key format error

**Solution:**
- Make sure key is PKCS#8 format
- Convert if needed:
```powershell
openssl pkcs8 -topk8 -inform PEM -outform PEM -in private.pem -out private-pkcs8.pem -nocrypt
```

### Issue 3: BCrypt password not matching

**Solution:**
- Ensure using same BCrypt cost factor
- Check password isn't trimmed/modified

---

## 9. Next Steps

**Identity Service complete!** You now have:
- ✅ User registration with validation
- ✅ Password hashing with BCrypt
- ✅ Login with JWT generation
- ✅ Profile retrieval

**Continue to:** [Part 04: Merchant Service](./part-04-merchant-service.md)

In Part 04, you'll build:
- Merchant entity and repository
- Merchant onboarding API
- Merchant ID generation (mer_xxxx format)

---

**End of Sprint 1, Part 03**

*Next: Merchant Service for Business Onboarding*
