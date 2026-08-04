# Hands-On Guide — Phase 4 Part 3: Controllers & Spring Security

## Goal

By the end of Part 3, you will have:
- AuthController with POST /v1/auth/register and POST /v1/auth/login
- SecurityConfig that permits auth endpoints without token
- Working registration tested with curl (user saved to DB, tokens returned)
- Working login tested with curl
- Swagger UI showing both endpoints with "Try it out"
- Your Git commit

## Prerequisites

- Part 2 completed (JwtService and AuthService compile)
- PostgreSQL running (docker compose up)
- identity-service starts without errors

---

## How the Request Flows

```
Client sends POST /v1/auth/register:

1. Request arrives at embedded Tomcat (port 8081)
2. Spring Security filter chain runs:
   ├── CsrfFilter: disabled (we use JWT not cookies)
   ├── Our check: Is path /v1/auth/** ? YES → permit (no auth needed)
   └── Request passes through
3. Spring routes to AuthController.register()
4. @Valid validates the request body:
   ├── email: not blank + valid format? ✓
   ├── password: not blank + 8-100 chars? ✓
   ├── fullName: not blank? ✓
   └── role: not blank? ✓
5. If validation fails → MethodArgumentNotValidException → GlobalExceptionHandler → 400
6. If validation passes → AuthService.register() runs
7. Returns ResponseEntity with 201 status + ApiResponse<AuthResponse>
```

---

## Step 3.1: Create AuthController

**What is a Controller?** The class that handles HTTP requests. Each method = one endpoint.

**Create file:** `identity-service/src/main/java/com/payflow/identity/controller/AuthController.java`

```java
package com.payflow.identity.controller;

import com.payflow.common.dto.ApiResponse;
// ApiResponse = our standard wrapper: {success: true/false, data: {...}, error: {...}}

import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
// Swagger annotations: generate interactive API docs automatically

import jakarta.validation.Valid;
// @Valid = "validate this request body before giving it to me"

import lombok.RequiredArgsConstructor;
// @RequiredArgsConstructor = Lombok generates constructor with all final fields
// This IS dependency injection (Spring passes AuthService via constructor)

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
// @RestController = @Controller + @ResponseBody
// Every method returns JSON directly (not a view/page)

@RequestMapping("/v1/auth")
// Base URL for all endpoints in this controller
// POST /v1/auth/register, POST /v1/auth/login

@RequiredArgsConstructor
// Lombok creates: AuthController(AuthService authService) { this.authService = authService; }

@Tag(name = "Authentication", description = "User registration, login, and token management")
// @Tag: Groups these endpoints under "Authentication" in Swagger UI
public class AuthController {

    private final AuthService authService;
    // Spring injects AuthService instance here (constructor injection via Lombok)

    @PostMapping("/register")
    // @PostMapping = handles POST requests to /v1/auth/register
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account with email/password and returns JWT tokens. "
            + "Email must be unique. Password is hashed with BCrypt before storage."
    )
    // @Operation: Describes this endpoint in Swagger UI
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "User registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "Invalid input (validation failed)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        // @Valid: Triggers validation annotations on RegisterRequest
        //   (@NotBlank, @Email, @Size) — if any fail, returns 400 automatically
        // @RequestBody: Parse JSON body into RegisterRequest object

        AuthResponse response = authService.register(request);
        // Call business logic — may throw DuplicateResourceException (409)

        return ResponseEntity
                .status(HttpStatus.CREATED)  // HTTP 201
                .body(ApiResponse.success(response));
        // Wraps in: {success: true, data: {accessToken, refreshToken, user}, timestamp}
    }

    @PostMapping("/login")
    @Operation(
        summary = "Login with email and password",
        description = "Validates credentials and returns JWT access token (15 min) "
            + "and refresh token (7 days)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Invalid email or password")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        // @Valid checks: email not blank + valid format, password not blank

        AuthResponse response = authService.login(request);
        // May throw PayflowException with INVALID_CREDENTIALS (401)

        return ResponseEntity.ok(ApiResponse.success(response));
        // HTTP 200 + wrapped response
    }
}
```

---

## Step 3.2: Create SecurityConfig

**What is this?** Configures which URLs need authentication and which are public.

**Create file:** `identity-service/src/main/java/com/payflow/identity/config/SecurityConfig.java`

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
// @Configuration: This class defines Spring beans (objects managed by Spring)

@EnableWebSecurity
// @EnableWebSecurity: Activates Spring Security for this application
// Without this, ALL endpoints require authentication by default!
public class SecurityConfig {

    @Bean
    // @Bean: This method returns an object that Spring will manage
    // SecurityFilterChain defines ALL security rules
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // CSRF (Cross-Site Request Forgery) protection:
            // Disable because we use JWT (not cookies/sessions)
            // CSRF only matters for cookie-based auth (browser sends cookies automatically)
            // With JWT: client must explicitly add Authorization header → CSRF not applicable

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // STATELESS = don't create HTTP sessions
            // Why? JWT is stateless — server doesn't store session data
            // Each request carries its own auth (the token)

            .authorizeHttpRequests(auth -> auth
                // Rules are checked in ORDER (first match wins)

                .requestMatchers("/v1/auth/**").permitAll()
                // /v1/auth/register, /v1/auth/login = PUBLIC (no auth needed)
                // permitAll() = anyone can access, even without a token

                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Swagger UI and OpenAPI spec = PUBLIC (developers need to see docs)

                .requestMatchers("/actuator/**").permitAll()
                // Health checks = PUBLIC (ALB needs to check /actuator/health)

                .anyRequest().authenticated()
                // Everything else = REQUIRES valid JWT token
                // If no valid token → Spring returns 401 Unauthorized
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
        // BCryptPasswordEncoder(12):
        //   12 = strength/cost factor (2^12 = 4096 iterations)
        //   Higher = more secure but slower
        //   12 = ~250ms per hash (good balance of security vs speed)
        //   10 = ~100ms (default, less secure)
        //   14 = ~1000ms (very secure but slow for login)
        //
        // This bean is injected into AuthService to hash passwords
    }
}
```

---

## Step 3.3: Verify with curl

### Test 1: Register a user

```cmd
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"merchant@test.com\",\"password\":\"SecureP@ss123\",\"fullName\":\"Test Merchant\",\"phone\":\"+919876543210\",\"role\":\"MERCHANT\"}"
```

**Expected response (201 Created):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "userId": "usr_Hk7mN3xQp2",
      "email": "merchant@test.com",
      "fullName": "Test Merchant",
      "role": "MERCHANT"
    }
  },
  "timestamp": "2026-07-20T10:30:00Z"
}
```

### Test 2: Register with same email (should fail)

```cmd
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"merchant@test.com\",\"password\":\"Another123\",\"fullName\":\"Duplicate\",\"role\":\"MERCHANT\"}"
```

**Expected response (409 Conflict):**
```json
{
  "success": false,
  "error": {
    "code": "DUPLICATE_EMAIL",
    "message": "A user with email 'merchant@test.com' already exists"
  },
  "timestamp": "2026-07-20T10:30:05Z"
}
```

### Test 3: Login

```cmd
curl -X POST http://localhost:8081/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"merchant@test.com\",\"password\":\"SecureP@ss123\"}"
```

**Expected: 200 OK with new tokens.**

### Test 4: Login with wrong password

```cmd
curl -X POST http://localhost:8081/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"merchant@test.com\",\"password\":\"WrongPassword\"}"
```

**Expected: 401 with INVALID_CREDENTIALS error.**

### Test 5: Swagger UI

Open http://localhost:8081/swagger-ui.html
- You should see "Authentication" section with 2 endpoints
- Click "Try it out" on register → paste JSON → Execute → see response

---

## Step 3.4: Verify in Database

```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "SELECT id, email, role, status FROM identity.users;"
```

**Expected:**
```
          id          |      email       |   role   | status
---------------------+------------------+----------+--------
 usr_Hk7mN3xQp2     | merchant@test.com | MERCHANT | ACTIVE
```

---

## Step 3.5: Git Commit

```cmd
git add identity-service/src/main/java/com/payflow/identity/controller/
git add identity-service/src/main/java/com/payflow/identity/config/
git commit -m "Phase 4 Part 3: AuthController + SecurityConfig (register/login endpoints working)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `controller/AuthController.java` | POST /register + POST /login endpoints |
| `config/SecurityConfig.java` | Public vs protected URLs, BCrypt password encoder |

---

## Next Step

→ Continue to **Phase 4 Part 4: Swagger & Testing**
