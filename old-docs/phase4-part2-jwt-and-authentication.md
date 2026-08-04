# Hands-On Guide — Phase 4 Part 2: JWT Service & Authentication Logic

## Goal

By the end of Part 2, you will have:
- JwtService that generates and validates JWT tokens
- AuthService with complete register + login business logic
- RegisterRequest, LoginRequest, AuthResponse DTOs
- Understanding of HOW JWT works (token structure, signing, validation)
- Understanding of HOW password hashing works (BCrypt)
- All code compiles, ready for controller in Part 3
- Git commit

## Prerequisites

- Part 1 completed (User entity, Flyway migration, application.yml ready)
- Identity-service starts without errors
- PostgreSQL has identity.users table

---

## How JWT Works (Full Explanation)

```
WHAT IS A JWT?

A JWT (JSON Web Token) is a signed string that contains user info.
It looks like: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfYWJjMTIzIn0.signature

Three parts separated by dots:

┌──────────────────────────────────────────────────────────────────────────┐
│  HEADER          .    PAYLOAD              .    SIGNATURE                  │
│  (algorithm)          (user data)               (proof of authenticity)   │
│                                                                            │
│  {"alg":"HS256"}      {"userId":"usr_abc",      HMACSHA256(               │
│                        "email":"a@b.com",       base64(header) + "." +    │
│                        "role":"MERCHANT",        base64(payload),          │
│                        "exp":1721402100}         SECRET_KEY)               │
│                                                                            │
│  Base64 encoded       Base64 encoded            Hex/Base64 encoded        │
└──────────────────────────────────────────────────────────────────────────┘

WHY IS IT USEFUL?

WITHOUT JWT (session-based):
├── User logs in → server creates SESSION (stored on server)
├── Server returns session ID in cookie
├── Every request: server looks up session in memory/database
├── PROBLEM: If you have 5 server instances → which has the session?
│   (Need sticky sessions or shared session store)
└── PROBLEM: Millions of users → millions of sessions in memory

WITH JWT (token-based, stateless):
├── User logs in → server creates JWT (signed string)
├── Server returns JWT to client (stored in localStorage or cookie)
├── Every request: client sends JWT in Authorization header
├── Server VALIDATES the signature (no database lookup needed!)
│   (Just verifies: was this signed with OUR secret key?)
├── ANY server instance can validate (no shared state needed!)
└── Scales infinitely — no server-side storage

HOW VALIDATION WORKS:

1. Client sends: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ...
2. Server takes the token apart:
   a. Base64-decode header → {"alg": "HS256"}
   b. Base64-decode payload → {"userId": "usr_abc", "exp": 1721402100}
   c. Re-compute signature: HMACSHA256(header + "." + payload, SECRET_KEY)
   d. Compare computed signature with the one in the token
3. If signatures MATCH → token is authentic (wasn't tampered with)
4. If signatures DON'T match → token is forged → reject!
5. Also check: is "exp" in the future? If expired → reject!

SECURITY:
├── Without the SECRET_KEY, you can't forge a valid signature
├── If anyone modifies the payload (e.g., change role to ADMIN),
│   the signature won't match → server rejects it
├── SHORT expiry (15 min) limits damage if token is stolen
└── Refresh token (7 days) allows getting new access tokens without re-login
```

---

## How Password Hashing Works (BCrypt)

```
WRONG WAY: Store plain text passwords in database
  Database row: { email: "user@test.com", password: "MyPass123" }
  Hacker steals database → sees ALL passwords → logs in as anyone! 💀

RIGHT WAY: Store HASHED passwords (one-way function)
  Database row: { email: "user@test.com", password_hash: "$2a$12$LJ3m4..." }
  Hacker steals database → sees hashes → CANNOT reverse them! ✅

HOW BCRYPT WORKS:

REGISTRATION:
  Input: "MyPass123"
  Process: BCrypt.encode("MyPass123") 
  Output: "$2a$12$LJ3m4rF8aHQ7u4Xk9Bx2eOPxZZqWm5RzVb5Kx7JHn3mN1LQZwCy6"
  Store: This 60-character string in database

LOGIN:
  Input: "MyPass123" (user types password again)
  Process: BCrypt.matches("MyPass123", "$2a$12$LJ3m4rF8aHQ7u4Xk9...")
  Result: TRUE → passwords match → login success!

  Input: "WrongPassword"
  Process: BCrypt.matches("WrongPassword", "$2a$12$LJ3m4rF8aHQ7u4Xk9...")
  Result: FALSE → doesn't match → login failed!

WHY BCrypt (not SHA-256 for passwords)?
├── SHA-256 is FAST (billions of hashes per second) → easy to brute-force
├── BCrypt is INTENTIONALLY SLOW (~250ms per hash with strength 12)
├── Attacker can only try 4 passwords per second (not 4 billion!)
├── BCrypt has built-in SALT (random bytes mixed in)
│   Same password → DIFFERENT hash each time!
│   "MyPass123" → "$2a$12$aaaa..." (first registration)
│   "MyPass123" → "$2a$12$bbbb..." (second registration)
│   Even if two users have same password → hashes are different!
└── BCrypt strength 12 = 2^12 = 4096 iterations (configurable slowness)
```

---

## Step 2.1: Create RegisterRequest DTO

**Create file:** `identity-service/src/main/java/com/payflow/identity/dto/RegisterRequest.java`

```java
package com.payflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /v1/auth/register.
 * 
 * Spring validates BEFORE calling our code:
 * - If email is blank → 400 with "Email is required"
 * - If password is < 8 chars → 400 with "Password must be between 8 and 100 characters"
 * 
 * These annotations = automatic validation (no if-statements needed).
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;
    // Must be valid format: user@domain.com
    // @Email checks: contains @, has domain part, basic format validation

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
    // Why min 8? Security best practice.
    // Why max 100? Prevent absurdly long passwords (BCrypt has 72-byte limit anyway).
    // We NEVER log or display this value!

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    private String phone;
    // Optional: No validation annotation = any value accepted (including null)

    @NotBlank(message = "Role is required")
    private String role;
    // Must be "CUSTOMER" or "MERCHANT"
    // Validated in AuthService (not via annotation — enum parsing)
}
```

---

## Step 2.2: Create LoginRequest DTO

**Create file:** `identity-service/src/main/java/com/payflow/identity/dto/LoginRequest.java`

```java
package com.payflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
    // No @Size here — during login we just check against stored hash
    // If they somehow have a short password from before we added the rule, they can still login
}
```

---

## Step 2.3: Create AuthResponse DTO

**Create file:** `identity-service/src/main/java/com/payflow/identity/dto/AuthResponse.java`

```java
package com.payflow.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned after successful register or login.
 * Contains tokens + user info.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    // Short-lived token (15 min) — sent in every API request
    // Header: Authorization: Bearer {accessToken}

    private String refreshToken;
    // Long-lived token (7 days) — used to get new access token
    // Only sent to POST /v1/auth/refresh endpoint

    private String tokenType;
    // Always "Bearer" (industry standard for JWT auth)

    private long expiresIn;
    // Access token lifetime in SECONDS (900 = 15 minutes)
    // Client uses this to know when to refresh

    private UserInfo user;
    // Basic user info (avoids extra API call after login)

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserInfo {
        private String userId;    // usr_Hk7mN3xQp2
        private String email;     // merchant@test.com
        private String fullName;  // Test Merchant
        private String role;      // MERCHANT
    }
}
```

---

## Step 2.4: Create JwtService

**Create file:** `identity-service/src/main/java/com/payflow/identity/service/JwtService.java`

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

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;
    // Read from application.yml: jwt.secret = "payflow-jwt-secret-key-..."
    // Must be at least 32 bytes (256 bits) for HMAC-SHA256

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;
    // 900000 ms = 15 minutes

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;
    // 604800000 ms = 7 days

    /**
     * Generate access token (15 min lifetime).
     * Contains: userId, email, role (used by other services for authorization)
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
     * Generate refresh token (7 day lifetime).
     * Contains: only userId (minimal data — just enough to issue new access token)
     */
    public String generateRefreshToken(String userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "REFRESH");
        return buildToken(claims, email, refreshTokenExpiry);
    }

    /**
     * Validate token and extract claims.
     * Returns null if: expired, tampered, wrong signature, malformed.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null; // Invalid token
        }
    }

    public String extractUserId(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("userId", String.class) : null;
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
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
```

---

## Step 2.5: Create AuthService

**Create file:** `identity-service/src/main/java/com/payflow/identity/service/AuthService.java`

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
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder (from SecurityConfig)
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Check duplicate email
        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new DuplicateResourceException("DUPLICATE_EMAIL",
                    "A user with email '" + request.getEmail() + "' already exists");
        }

        // 2. Parse and validate role
        User.Role role;
        try {
            role = User.Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PayflowException("INVALID_ROLE",
                    "Role must be CUSTOMER or MERCHANT", HttpStatus.BAD_REQUEST);
        }

        // 3. Create user entity
        User user = User.builder()
                .id(IdGenerator.userId())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .role(role)
                .build();

        // 4. Save to database
        userRepository.save(user);
        log.info("User registered: {} ({})", user.getId(), user.getEmail());

        // 5. Generate tokens and return
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new PayflowException("INVALID_CREDENTIALS",
                        "Email or password is incorrect", HttpStatus.UNAUTHORIZED));

        // 2. Check account status
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new PayflowException("ACCOUNT_SUSPENDED",
                    "Your account has been suspended", HttpStatus.FORBIDDEN);
        }

        // 3. Verify password (BCrypt comparison)
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new PayflowException("INVALID_CREDENTIALS",
                    "Email or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        // 4. Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: {} ({})", user.getId(), user.getEmail());
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name()))
                .refreshToken(jwtService.generateRefreshToken(user.getId(), user.getEmail()))
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

---

## Step 2.6: Create UserRepository

**File:** `identity-service/src/main/java/com/payflow/identity/repository/UserRepository.java`

```java
package com.payflow.identity.repository;

import com.payflow.identity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

---

## Step 2.7: Verify (Compilation)

```cmd
cd payflow-payment-gateway
mvn clean compile -pl identity-service -am
```

Expected: `BUILD SUCCESS` (all classes compile without errors).

---

## Step 2.8: Git Commit

```cmd
git add identity-service/src/main/java/com/payflow/identity/dto/
git add identity-service/src/main/java/com/payflow/identity/service/
git add identity-service/src/main/java/com/payflow/identity/repository/
git commit -m "Phase 4 Part 2: JWT service + AuthService (register, login, password hashing)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `dto/RegisterRequest.java` | Input for register (validated: @Email, @Size) |
| `dto/LoginRequest.java` | Input for login |
| `dto/AuthResponse.java` | Output: access token + refresh token + user info |
| `service/JwtService.java` | Generate + validate JWT tokens (HMAC-SHA256) |
| `service/AuthService.java` | Register (BCrypt hash) + Login (BCrypt verify) |
| `repository/UserRepository.java` | findByEmail, existsByEmail |

---

## Interview Notes

**Q: "How does JWT work in your system?"**
> "On login, server generates a short-lived access token (15 min) containing userId, email, and role, signed with HMAC-SHA256. Client sends it in every request header. Any service can validate by verifying the signature — no database lookup needed. For long sessions, a 7-day refresh token allows getting new access tokens without re-login."

**Q: "Why BCrypt for passwords?"**
> "BCrypt is intentionally slow (~250ms per hash with strength 12), making brute-force attacks impractical. It includes a built-in random salt so identical passwords produce different hashes. SHA-256 is too fast (billions/sec) — an attacker could try every possible password in hours."

**Q: "What if an access token is stolen?"**
> "Damage is limited to 15 minutes (short expiry). For critical operations we can also verify the user's status in the database. Refresh tokens can be revoked by deleting from the database — the attacker can't get new access tokens."

---

## Next Step

→ Continue to **Phase 4 Part 3: Controllers & Security Configuration**
