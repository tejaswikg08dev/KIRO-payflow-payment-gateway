# Sprint 1, Part 06: JWT Authentication

**Duration:** 2-3 hours  
**Prerequisites:** Part 05 completed, database tables created

---

## 1. What We're Building

In this part, you'll implement the **JWT authentication service** - the core logic for generating and validating tokens.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     JWT SERVICE RESPONSIBILITIES                             │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       JwtService                                     │   │
│  │                                                                      │   │
│  │  generateAccessToken(userId, email, role)                           │   │
│  │     │                                                                │   │
│  │     └──► Creates JWT with:                                          │   │
│  │         • Claims: userId, email, role, type="ACCESS"                │   │
│  │         • Subject: email                                            │   │
│  │         • Expiration: 15 minutes                                    │   │
│  │         • Signed with: HMAC secret key                              │   │
│  │                                                                      │   │
│  │  generateRefreshToken(userId, email)                                │   │
│  │     │                                                                │   │
│  │     └──► Creates JWT with:                                          │   │
│  │         • Claims: userId, type="REFRESH"                            │   │
│  │         • Subject: email                                            │   │
│  │         • Expiration: 7 days                                        │   │
│  │         • NOT stored in database                                    │   │
│  │                                                                      │   │
│  │  validateToken(token)                                               │   │
│  │     │                                                                │   │
│  │     └──► Verifies JWT:                                              │   │
│  │         • Check signature with HMAC key                             │   │
│  │         • Check expiration                                          │   │
│  │         • Extract claims                                            │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       AuthService                                    │   │
│  │                                                                      │   │
│  │  register(request)                                                  │   │
│  │     │                                                                │   │
│  │     └──► Create user account                                        │   │
│  │         • Parse role from request                                   │   │
│  │         • Hash password with BCrypt                                 │   │
│  │         • Generate ID with IdGenerator                              │   │
│  │         • Save to database                                          │   │
│  │         • Generate tokens                                           │   │
│  │                                                                      │   │
│  │  login(email, password)                                             │   │
│  │     │                                                                │   │
│  │     └──► Authenticate user                                          │   │
│  │         • Find user by email                                        │   │
│  │         • Check status is ACTIVE                                    │   │
│  │         • Verify password                                           │   │
│  │         • Update lastLoginAt                                        │   │
│  │         • Generate tokens                                           │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive


### 2.1 HMAC Secret Key for JWT Signing

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HMAC SYMMETRIC ENCRYPTION                                 │
│                                                                              │
│  PayFlow uses HMAC-SHA256 (HS256) for JWT signing:                         │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      SYMMETRIC KEY                                   │   │
│  │                                                                      │   │
│  │  Same secret key used for:                                          │   │
│  │  • Signing tokens (creating JWT)                                    │   │
│  │  • Verifying tokens (validating JWT)                                │   │
│  │                                                                      │   │
│  │  ┌───────────────────────────────────────────────────────────────┐  │   │
│  │  │          jwt.secret = "your-256-bit-secret"                   │  │   │
│  │  │                          │                                     │  │   │
│  │  │                          ▼                                     │  │   │
│  │  │   Sign ──────────────► JWT ◄────────────── Verify             │  │   │
│  │  │   (Identity Service)        (Identity Service / Gateway)      │  │   │
│  │  └───────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  WHY HMAC FOR THIS PROJECT?                                                 │
│  ──────────────────────────                                                 │
│  • Simpler than RSA key pairs                                              │
│  • Single service (identity-service) handles all auth                      │
│  • Secret key stored in application.yml                                    │
│  • For production: Use environment variables or secrets manager            │
│                                                                              │
│  SECURITY NOTE:                                                             │
│  ───────────────                                                            │
│  The secret key MUST be:                                                    │
│  • At least 256 bits (32 bytes) for HS256                                  │
│  • Kept secret (never commit to git!)                                      │
│  • Rotated periodically                                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Token Generation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LOGIN → TOKEN GENERATION FLOW                             │
│                                                                              │
│  POST /v1/auth/login                                                        │
│  { "email": "john@example.com", "password": "secret123" }                  │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 1. FIND USER                                                         │   │
│  │    userRepository.findByEmail("john@example.com")                   │   │
│  │    │                                                                 │   │
│  │    ├── Not found? → 401 "Email or password is incorrect"            │   │
│  │    └── Found → Continue                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 2. CHECK ACCOUNT STATUS                                              │   │
│  │    │                                                                 │   │
│  │    ├── status != ACTIVE? → 403 "Account has been suspended"         │   │
│  │    └── OK → Continue                                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 3. VERIFY PASSWORD                                                   │   │
│  │    passwordEncoder.matches("secret123", user.getPasswordHash())     │   │
│  │    │                                                                 │   │
│  │    ├── No match? → 401 "Email or password is incorrect"             │   │
│  │    └── Match → Continue                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 4. UPDATE LAST LOGIN & GENERATE TOKENS                               │   │
│  │                                                                      │   │
│  │    user.setLastLoginAt(Instant.now());                              │   │
│  │    userRepository.save(user);                                       │   │
│  │                                                                      │   │
│  │    Access Token:                                                    │   │
│  │    ┌──────────────────────────────────────────────────────────┐    │   │
│  │    │ Header: { "alg": "HS256", "typ": "JWT" }                 │    │   │
│  │    │ Payload: {                                                │    │   │
│  │    │   "userId": "a1B2c3D4e5",                                │    │   │
│  │    │   "email": "john@example.com",                           │    │   │
│  │    │   "role": "MERCHANT",                                    │    │   │
│  │    │   "type": "ACCESS",                                      │    │   │
│  │    │   "sub": "john@example.com",                             │    │   │
│  │    │   "iat": 1609459200,                                     │    │   │
│  │    │   "exp": 1609460100 (15 min later)                       │    │   │
│  │    │ }                                                         │    │   │
│  │    │ Signature: HMAC-SHA256(header + payload, SECRET)         │    │   │
│  │    └──────────────────────────────────────────────────────────┘    │   │
│  │                                                                      │   │
│  │    Refresh Token:                                                   │   │
│  │    ┌──────────────────────────────────────────────────────────┐    │   │
│  │    │ Also a JWT (NOT stored in database):                     │    │   │
│  │    │   "userId": "a1B2c3D4e5",                                │    │   │
│  │    │   "type": "REFRESH",                                     │    │   │
│  │    │   "exp": (7 days later)                                  │    │   │
│  │    └──────────────────────────────────────────────────────────┘    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│         │                                                                    │
│         ▼                                                                    │
│  Response:                                                                   │
│  {                                                                           │
│    "accessToken": "eyJhbGciOiJIUzI1NiIs...",                               │
│    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",                              │
│    "tokenType": "Bearer",                                                   │
│    "expiresIn": 900,                                                        │
│    "user": {                                                                │
│      "userId": "a1B2c3D4e5",                                               │
│      "email": "john@example.com",                                          │
│      "fullName": "John Doe",                                               │
│      "role": "MERCHANT"                                                    │
│    }                                                                        │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Identity Service should start with database migrations applied
cd identity-service
mvn spring-boot:run

# Check tables exist
docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dt identity.*"
# Expected: users table
```

---

## 4. Step-by-Step Implementation


### Step 4.1: Create JwtService

**File: `identity-service/src/main/java/com/payflow/identity/service/JwtService.java`**

```java
package com.payflow.identity.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Service — Creates and validates JSON Web Tokens.
 *
 * Access Token: Short-lived (15 min), used for API authentication.
 * Refresh Token: Long-lived (7 days), used to get new access tokens.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    /**
     * Generate an access token for a user.
     */
    public String generateAccessToken(String userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("role", role);
        claims.put("type", "ACCESS");

        return buildToken(claims, email, accessTokenExpiry);
    }

    /**
     * Generate a refresh token for a user.
     */
    public String generateRefreshToken(String userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "REFRESH");

        return buildToken(claims, email, refreshTokenExpiry);
    }

    /**
     * Validate a token and extract claims.
     * Returns null if token is invalid or expired.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract user ID from a valid token.
     */
    public String extractUserId(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("userId", String.class) : null;
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        return claims == null || claims.getExpiration().before(new Date());
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiry) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```


**Understanding JwtService:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    JWT SERVICE EXPLAINED                                     │
│                                                                              │
│  CONFIGURATION VALUES (from application.yml):                               │
│  ────────────────────────────────────────────                               │
│  jwt.secret           → HMAC secret key (256+ bits)                        │
│  jwt.access-token-expiry  → 900000 (15 minutes in ms)                      │
│  jwt.refresh-token-expiry → 604800000 (7 days in ms)                       │
│                                                                              │
│  TOKEN CLAIMS:                                                              │
│  ─────────────                                                              │
│  Access Token:                  Refresh Token:                              │
│  ┌─────────────────────┐       ┌─────────────────────┐                     │
│  │ userId: "a1B2c3D4e5"│       │ userId: "a1B2c3D4e5"│                     │
│  │ email: "j@ex.com"   │       │ type: "REFRESH"      │                     │
│  │ role: "MERCHANT"    │       │ sub: "j@example.com" │                     │
│  │ type: "ACCESS"      │       │ exp: (7 days)        │                     │
│  │ sub: "j@example.com"│       └─────────────────────┘                     │
│  │ exp: (15 min)       │                                                    │
│  └─────────────────────┘                                                    │
│                                                                              │
│  WHY HMAC (getSigningKey)?                                                  │
│  ─────────────────────────                                                  │
│  • Keys.hmacShaKeyFor() creates HMAC-SHA key from bytes                    │
│  • Same key signs AND verifies tokens                                      │
│  • Secret must be at least 256 bits (32 chars)                             │
│                                                                              │
│  WHY RETURN null ON VALIDATION FAILURE?                                     │
│  ──────────────────────────────────────                                     │
│  • Cleaner error handling                                                  │
│  • Caller checks: if (claims == null) → invalid token                      │
│  • Avoids try-catch blocks in calling code                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Step 4.2: Create SecurityConfig

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())  // Disable CSRF (we use JWT, not cookies)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // No sessions
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no auth required)
                        .requestMatchers("/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 (secure, ~250ms per hash)
    }
}
```


**Understanding SecurityConfig:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SECURITY CONFIG EXPLAINED                                 │
│                                                                              │
│  CSRF DISABLED - WHY?                                                       │
│  ────────────────────                                                       │
│  • CSRF attacks exploit cookie-based auth                                  │
│  • We use JWT in Authorization header, not cookies                         │
│  • No cookies = no CSRF risk                                               │
│                                                                              │
│  STATELESS SESSION - WHY?                                                   │
│  ────────────────────────                                                   │
│  • No server-side session storage                                          │
│  • Each request is independent                                             │
│  • JWT contains all auth info                                              │
│  • Better for microservices (no session sharing needed)                    │
│                                                                              │
│  ENDPOINT PERMISSIONS:                                                      │
│  ─────────────────────                                                      │
│  /v1/auth/**         → PUBLIC  (login, register)                           │
│  /swagger-ui/**      → PUBLIC  (API docs)                                  │
│  /v3/api-docs/**     → PUBLIC  (OpenAPI spec)                              │
│  /actuator/**        → PUBLIC  (health checks)                             │
│  everything else     → REQUIRES AUTHENTICATION                             │
│                                                                              │
│  BCRYPT STRENGTH 12:                                                        │
│  ──────────────────                                                         │
│  • Strength 10 = ~100ms per hash                                           │
│  • Strength 12 = ~250ms per hash (recommended)                             │
│  • Strength 14 = ~1000ms per hash                                          │
│  Higher = more secure but slower login                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Step 4.3: Create AuthService

**File: `identity-service/src/main/java/com/payflow/identity/service/AuthService.java`**

```java
package com.payflow.identity.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.PayflowException;
import com.payflow.common.util.IdGenerator;
import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.model.User;
import com.payflow.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Register a new user.
     * 1. Check if email already exists
     * 2. Hash password with BCrypt
     * 3. Save user to database
     * 4. Generate JWT tokens
     * 5. Return auth response
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("DUPLICATE_EMAIL",
                    "A user with email '" + request.getEmail() + "' already exists");
        }

        // Parse role
        User.Role role;
        try {
            role = User.Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PayflowException("INVALID_ROLE",
                    "Role must be CUSTOMER or MERCHANT", HttpStatus.BAD_REQUEST);
        }

        // Create user entity
        User user = User.builder()
                .id(IdGenerator.userId())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .role(role)
                .emailVerified(false)
                .status(User.UserStatus.ACTIVE)
                .build();

        // Save to database
        userRepository.save(user);
        log.info("User registered: {} ({})", user.getId(), user.getEmail());

        // Generate tokens and return
        return buildAuthResponse(user);
    }

    /**
     * Login with email and password.
     * 1. Find user by email
     * 2. Verify password with BCrypt
     * 3. Generate JWT tokens
     * 4. Update last login timestamp
     * 5. Return auth response
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Find user
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new PayflowException("INVALID_CREDENTIALS",
                        "Email or password is incorrect", HttpStatus.UNAUTHORIZED));

        // Check account status
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new PayflowException("ACCOUNT_SUSPENDED",
                    "Your account has been suspended", HttpStatus.FORBIDDEN);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new PayflowException("INVALID_CREDENTIALS",
                    "Email or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: {} ({})", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15 minutes in seconds
                .user(AuthResponse.UserInfo.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .build())
                .build();
    }
}
```


**Understanding AuthService:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTH SERVICE EXPLAINED                                    │
│                                                                              │
│  KEY DEPENDENCIES:                                                          │
│  ─────────────────                                                          │
│  • UserRepository - Database access                                        │
│  • PasswordEncoder - BCrypt password hashing                               │
│  • JwtService - Token generation                                           │
│  • IdGenerator - Creates 10-char user IDs (from common-lib)                │
│                                                                              │
│  EXCEPTION HANDLING:                                                        │
│  ───────────────────                                                        │
│  Uses exceptions from common-lib (NOT custom exception classes):           │
│  • DuplicateResourceException - Email already exists                       │
│  • PayflowException - Generic exception with code, message, status         │
│                                                                              │
│  REGISTER FLOW:                                                             │
│  ──────────────                                                             │
│  1. existsByEmail(email) → true? DuplicateResourceException               │
│  2. User.Role.valueOf(role) → invalid? PayflowException(BAD_REQUEST)       │
│  3. IdGenerator.userId() → "a1B2c3D4e5" (10-char random ID)               │
│  4. passwordEncoder.encode(password) → BCrypt hash                         │
│  5. userRepository.save(user)                                              │
│  6. Generate access + refresh tokens                                       │
│  7. Return AuthResponse with UserInfo                                      │
│                                                                              │
│  LOGIN FLOW:                                                                │
│  ──────────                                                                 │
│  1. findByEmail() → not found? PayflowException(UNAUTHORIZED)              │
│  2. Check status != ACTIVE? PayflowException(FORBIDDEN)                    │
│  3. passwordEncoder.matches() → false? PayflowException(UNAUTHORIZED)      │
│  4. Update lastLoginAt timestamp                                           │
│  5. Generate access + refresh tokens                                       │
│  6. Return AuthResponse                                                    │
│                                                                              │
│  WHY SAME ERROR FOR "USER NOT FOUND" AND "WRONG PASSWORD"?                 │
│  ──────────────────────────────────────────────────────────                 │
│  Security best practice! Different errors let attackers:                   │
│  • Enumerate valid emails ("user not found" = invalid email)              │
│  • Focus brute force on valid accounts                                     │
│  Same error prevents email enumeration attacks.                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### 5.1 Build and Run

```powershell
cd identity-service
mvn clean package -DskipTests
mvn spring-boot:run
```

### 5.2 Verify Service Starts

Expected output:
```
INFO  --- Flyway : Successfully applied 1 migration
INFO  --- Started IdentityServiceApplication in X.XXX seconds
```

---

## 6. File Structure

After completing this part:

```
identity-service/
├── src/main/java/com/payflow/identity/
│   ├── config/
│   │   └── SecurityConfig.java     ← BCrypt + Security rules
│   ├── dto/
│   │   ├── AuthResponse.java       ← With nested UserInfo class
│   │   ├── LoginRequest.java
│   │   └── RegisterRequest.java
│   ├── model/
│   │   └── User.java               ← With Role & UserStatus enums
│   ├── repository/
│   │   └── UserRepository.java
│   └── service/
│       ├── AuthService.java        ← Register/Login logic
│       └── JwtService.java         ← Token generation
```

**Important Differences from Generic Tutorials:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PAYFLOW-SPECIFIC DESIGN CHOICES                           │
│                                                                              │
│  WHAT GENERIC TUTORIALS SHOW     │ WHAT PAYFLOW USES                       │
│  ─────────────────────────────────────────────────────────────────────────  │
│  RSA key pair (RS256)             │ HMAC secret key (HS256)                 │
│  RefreshToken entity in DB        │ Refresh token is a JWT (no DB storage) │
│  Multiple exception classes       │ PayflowException from common-lib       │
│  Role.MERCHANT default            │ Role parsed from request               │
│  firstName, lastName              │ fullName single field                   │
│  UserDto separate class           │ UserInfo nested in AuthResponse        │
│  entity package                   │ model package                           │
│  UUID IDs                         │ 10-char String IDs (IdGenerator)       │
│  Account locking logic            │ Simple status check (ACTIVE only)      │
│                                                                              │
│  These choices are intentional and match PayFlow's design decisions.        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ JWT Token Generation with HMAC                                          │
│     • HS256 algorithm (HMAC + SHA-256)                                     │
│     • Single secret key for signing and verification                       │
│     • Access token (15 min) vs Refresh token (7 days)                      │
│     • Custom claims (userId, email, role, type)                            │
│                                                                              │
│  ✅ Authentication Flow                                                     │
│     • Find user → Check status → Verify password → Generate tokens         │
│     • Same error message for "not found" and "wrong password"              │
│     • Update lastLoginAt on successful login                               │
│                                                                              │
│  ✅ Spring Security Configuration                                          │
│     • CSRF disabled (using JWT, not cookies)                               │
│     • Stateless session (no server-side sessions)                          │
│     • Endpoint-based authorization (permitAll vs authenticated)            │
│                                                                              │
│  ✅ Password Hashing                                                       │
│     • BCrypt with strength 12                                              │
│     • passwordEncoder.encode() for hashing                                 │
│     • passwordEncoder.matches() for verification                           │
│                                                                              │
│  ✅ Exception Handling                                                     │
│     • Uses common-lib exceptions (PayflowException)                        │
│     • HTTP status codes: 401 (unauthorized), 403 (forbidden)               │
│     • Error codes for client-side handling                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Q&A / Troubleshooting

### Q1: "JWT secret key too short" error

**Cause:** HMAC-SHA256 requires at least 256 bits (32 bytes) key.

**Fix:**
```yaml
# In application.yml, ensure secret is at least 32 characters:
jwt:
  secret: "your-super-secret-key-that-is-at-least-32-characters-long"
```

### Q2: "No PasswordEncoder mapped for the id 'null'"

**Cause:** PasswordEncoder bean not found.

**Fix:**
Ensure SecurityConfig has the `@Bean` annotated `passwordEncoder()` method.

### Q3: "Cannot find symbol: IdGenerator"

**Cause:** common-lib dependency not included.

**Fix:**
```xml
<!-- In identity-service pom.xml -->
<dependency>
    <groupId>com.payflow</groupId>
    <artifactId>common-lib</artifactId>
</dependency>
```

### Q4: "Cannot find symbol: User.Role or User.UserStatus"

**Cause:** Role and UserStatus are inner enums in User class.

**Fix:**
```java
// Correct usage:
User.Role role = User.Role.CUSTOMER;
User.UserStatus status = User.UserStatus.ACTIVE;
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS TO EXPLORE                                 │
│                                                                              │
│  Token Refresh Endpoint                                                     │
│  ─────────────────────                                                      │
│  The refresh token can be used to get a new access token without           │
│  re-entering credentials. This is implemented in the controller.           │
│                                                                              │
│  Key Rotation                                                               │
│  ────────────                                                               │
│  In production, periodically rotate the JWT secret key.                    │
│  Use a secrets manager (AWS Secrets Manager, HashiCorp Vault).             │
│                                                                              │
│  OAuth 2.0 / OpenID Connect                                                │
│  ──────────────────────────                                                │
│  Industry standards for authentication. Consider if you need              │
│  third-party login (Google, GitHub) or SSO.                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT'S NEXT                                               │
│                                                                              │
│  ✅ Part 06 COMPLETE: JWT Authentication                                    │
│                                                                              │
│  NEXT: Part 07 - Identity Controllers                                       │
│  ───────────────────────────────────                                        │
│  In Part 07, we'll create:                                                  │
│  • AuthController for /v1/auth endpoints                                   │
│  • Global exception handler                                                │
│  • Request validation                                                       │
│  • Response formatting                                                      │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  IDENTITY SERVICE BUILD PROGRESS                                    │   │
│  │                                                                      │   │
│  │  Part 04: Setup ✅                                                  │   │
│  │  Part 05: Database ✅      - Entity, migration, repository          │   │
│  │  Part 06: JWT Auth ✅      - JwtService, AuthService, Security      │   │
│  │  Part 07: Controllers      - REST endpoints                         │   │
│  │  Part 08: Swagger          - API docs                               │   │
│  │  Part 09: Testing          - Tests                                  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Continue to: part-07-identity-controllers.md                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 06 Complete!** 🎉

You now have:
- JwtService for HMAC-based token generation
- AuthService for authentication logic (register/login)
- SecurityConfig with BCrypt and endpoint security
- All using common-lib exceptions and IdGenerator
