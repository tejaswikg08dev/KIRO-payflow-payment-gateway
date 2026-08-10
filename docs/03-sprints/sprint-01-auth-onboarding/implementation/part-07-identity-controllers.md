# Sprint 1, Part 07: Identity Controllers

**Duration:** 1.5-2 hours  
**Prerequisites:** Part 06 completed, AuthService and JwtService implemented

---

## 1. What We're Building

In this part, you'll create the **REST controllers** - the HTTP endpoints that clients call.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     IDENTITY SERVICE ENDPOINTS                               │
│                                                                              │
│  POST /v1/auth/register                                                     │
│  ─────────────────────                                                      │
│  Request:  { email, password, fullName, phone, role }                       │
│  Response: { success: true, data: { accessToken, refreshToken, user } }     │
│  Status:   201 Created                                                      │
│                                                                              │
│  POST /v1/auth/login                                                        │
│  ──────────────────                                                         │
│  Request:  { email, password }                                              │
│  Response: { success: true, data: { accessToken, refreshToken, user } }     │
│  Status:   200 OK                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 REST Controller Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REQUEST FLOW                                              │
│                                                                              │
│  HTTP Request                                                                │
│       │                                                                      │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Servlet Filter Chain                                                 │   │
│  │ • Spring Security filters                                           │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Controller                                                           │   │
│  │ • @RestController                                                   │   │
│  │ • @RequestMapping("/v1/auth")                                       │   │
│  │ • Method: login(@Valid @RequestBody LoginRequest)                   │   │
│  │                                                                      │   │
│  │ What Controller does:                                               │   │
│  │ 1. Validate input (@Valid)                                         │   │
│  │ 2. Call service layer                                              │   │
│  │ 3. Wrap response in ApiResponse                                    │   │
│  │ 4. Return with HTTP status                                         │   │
│  │                                                                      │   │
│  │ What Controller does NOT do:                                        │   │
│  │ ❌ Business logic                                                   │   │
│  │ ❌ Database access                                                  │   │
│  │ ❌ Exception handling (common-lib handles it)                       │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Service Layer (AuthService)                                          │   │
│  │ • Business logic                                                    │   │
│  │ • Throws exceptions from common-lib                                 │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                          │
│                                   ▼                                          │
│                            Repository Layer                                  │
│                                   │                                          │
│                                   ▼                                          │
│                              Database                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.2 ApiResponse Wrapper


```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ApiResponse FROM common-lib                               │
│                                                                              │
│  All PayFlow APIs return responses wrapped in ApiResponse<T>:               │
│                                                                              │
│  SUCCESS RESPONSE:                                                          │
│  ─────────────────                                                          │
│  {                                                                          │
│    "success": true,                                                         │
│    "data": {                                                                │
│      "accessToken": "eyJhbGci...",                                         │
│      "refreshToken": "eyJhbGci...",                                        │
│      "tokenType": "Bearer",                                                │
│      "expiresIn": 900,                                                     │
│      "user": {                                                             │
│        "userId": "a1B2c3D4e5",                                            │
│        "email": "john@example.com",                                       │
│        "fullName": "John Doe",                                            │
│        "role": "MERCHANT"                                                 │
│      }                                                                     │
│    },                                                                       │
│    "timestamp": "2024-01-15T10:30:00Z"                                     │
│  }                                                                          │
│                                                                              │
│  ERROR RESPONSE (handled by GlobalExceptionHandler in common-lib):          │
│  ────────────────────────────────────────────────────────────               │
│  {                                                                          │
│    "success": false,                                                        │
│    "error": {                                                               │
│      "code": "INVALID_CREDENTIALS",                                        │
│      "message": "Email or password is incorrect"                           │
│    },                                                                       │
│    "timestamp": "2024-01-15T10:30:00Z"                                     │
│  }                                                                          │
│                                                                              │
│  WHY WRAP ALL RESPONSES?                                                    │
│  ───────────────────────                                                    │
│  • Consistent structure for frontend                                       │
│  • Easy to check success/failure                                           │
│  • Standardized error format                                               │
│  • Includes timestamp for debugging                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 HTTP Status Codes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    HTTP STATUS CODES FOR AUTH                                │
│                                                                              │
│  2xx SUCCESS                                                                │
│  ───────────                                                                │
│  200 OK           - Request successful (login)                             │
│  201 Created      - Resource created (register)                            │
│                                                                              │
│  4xx CLIENT ERRORS                                                          │
│  ────────────────                                                           │
│  400 Bad Request  - Invalid input (validation failed, invalid role)       │
│  401 Unauthorized - Authentication failed (wrong password)                 │
│  403 Forbidden    - Account suspended                                      │
│  409 Conflict     - Resource already exists (duplicate email)             │
│                                                                              │
│  OUR MAPPINGS:                                                              │
│  ─────────────                                                              │
│  register success           → 201 Created                                  │
│  login success              → 200 OK                                       │
│  invalid credentials        → 401 Unauthorized                             │
│  email already exists       → 409 Conflict (DuplicateResourceException)    │
│  account suspended          → 403 Forbidden                                │
│  validation failed          → 400 Bad Request                              │
│  invalid role               → 400 Bad Request                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# AuthService and JwtService should be implemented
cd identity-service
mvn clean compile
# Expected: BUILD SUCCESS
```

---

## 4. Step-by-Step Implementation


### Step 4.1: Create AuthController

**File: `identity-service/src/main/java/com/payflow/identity/controller/AuthController.java`**

```java
package com.payflow.identity.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.exception.PayflowException;
import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.ProfileResponse;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.service.AuthService;
import com.payflow.identity.service.JwtService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, and token management")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT tokens")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password", description = "Validates credentials and returns JWT tokens")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile from JWT token", description = "Returns user info extracted from the Authorization header token")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or missing token")
    })
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Extract token from header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new PayflowException("INVALID_TOKEN", "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }
        
        String token = authHeader.substring(7);
        Claims claims = jwtService.validateToken(token);
        
        if (claims == null) {
            throw new PayflowException("INVALID_TOKEN", "Token is invalid or expired", HttpStatus.UNAUTHORIZED);
        }
        
        // Extract claims
        String userId = claims.get("userId", String.class);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        
        ProfileResponse profile = ProfileResponse.builder()
                .userId(userId)
                .email(email)
                .role(role)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
```

**Understanding AuthController:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTH CONTROLLER EXPLAINED                                 │
│                                                                              │
│  CLASS ANNOTATIONS:                                                         │
│  ─────────────────                                                          │
│  @RestController      → Marks class as REST API controller                 │
│  @RequestMapping("/v1/auth") → Base path for all endpoints                 │
│  @RequiredArgsConstructor → Lombok generates constructor for final fields  │
│  @Tag(...)           → Swagger/OpenAPI documentation                       │
│                                                                              │
│  METHOD ANNOTATIONS:                                                        │
│  ──────────────────                                                         │
│  @PostMapping("/register") → Maps POST /v1/auth/register                   │
│  @Operation(...)     → Swagger description for endpoint                    │
│  @ApiResponses(...)  → Documents possible response codes                   │
│                                                                              │
│  PARAMETER ANNOTATIONS:                                                     │
│  ─────────────────────                                                      │
│  @Valid              → Triggers Bean Validation on request body            │
│  @RequestBody        → Deserializes JSON to Java object                    │
│                                                                              │
│  KEY DESIGN CHOICES:                                                        │
│  ───────────────────                                                        │
│  • Returns ApiResponse<AuthResponse>, NOT raw AuthResponse                 │
│  • ApiResponse.success(data) wraps the response                            │
│  • No try-catch - GlobalExceptionHandler (common-lib) handles errors       │
│  • Controller is thin - all logic in AuthService                           │
│  • Swagger annotations for API documentation                               │
│                                                                              │
│  WHY ResponseEntity<ApiResponse<AuthResponse>>?                            │
│  ────────────────────────────────────────────                               │
│  • ResponseEntity: Control HTTP status code                                │
│  • ApiResponse: Standard wrapper for all PayFlow APIs                      │
│  • AuthResponse: The actual data (tokens + user info)                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Step 4.2: Create ProfileResponse DTO

**File: `identity-service/src/main/java/com/payflow/identity/dto/ProfileResponse.java`**

```java
package com.payflow.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /v1/auth/profile endpoint.
 * Returns user info extracted from JWT token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private String userId;
    private String email;
    private String role;
    private String merchantId;  // Will be populated by frontend after calling Merchant Service
}
```

**Why ProfileResponse?**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PROFILE ENDPOINT PURPOSE                                  │
│                                                                              │
│  The frontend needs the userId to:                                          │
│  • Fetch merchant data: GET /v1/merchants/by-user/{userId}                 │
│  • Create merchant during onboarding: POST /v1/merchants                   │
│                                                                              │
│  Flow:                                                                       │
│  ──────                                                                      │
│  1. Frontend has JWT token (stored after login)                             │
│  2. Frontend calls GET /v1/auth/profile with Bearer token                  │
│  3. Backend extracts claims from JWT and returns user info                  │
│  4. Frontend uses userId to interact with merchant service                  │
│                                                                              │
│  Why not decode JWT on frontend?                                            │
│  ───────────────────────────────                                            │
│  • JWT validation requires the secret key                                   │
│  • Secret key must NEVER be exposed to frontend                             │
│  • Server-side validation is more secure                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---


### Step 4.3: Understanding Common-Lib Integration

The AuthController uses several classes from `common-lib`:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMMON-LIB COMPONENTS USED                                │
│                                                                              │
│  1. ApiResponse<T> (from com.payflow.common.dto)                           │
│  ───────────────────────────────────────────────                            │
│  Wrapper for all API responses:                                             │
│  • ApiResponse.success(data) - Wraps successful response                   │
│  • ApiResponse.error(code, message) - Wraps error response                 │
│                                                                              │
│  2. GlobalExceptionHandler (from com.payflow.common.exception)             │
│  ────────────────────────────────────────────────────────────               │
│  Handles all exceptions thrown by AuthService:                              │
│  • PayflowException → Returns appropriate HTTP status                      │
│  • DuplicateResourceException → Returns 409 Conflict                       │
│  • MethodArgumentNotValidException → Returns 400 Bad Request               │
│                                                                              │
│  3. Exception Classes (from com.payflow.common.exception)                  │
│  ─────────────────────────────────────────────────────────                  │
│  • PayflowException - Generic exception with code, message, status         │
│  • DuplicateResourceException - For duplicate email                        │
│  • ResourceNotFoundException - For user not found (if needed)              │
│                                                                              │
│  4. IdGenerator (from com.payflow.common.util)                             │
│  ─────────────────────────────────────────────                              │
│  Used in AuthService to generate user IDs:                                 │
│  • IdGenerator.userId() → "a1B2c3D4e5" (10-char random)                   │
│                                                                              │
│  WHY COMMON-LIB?                                                            │
│  ──────────────                                                             │
│  • Consistent response format across all microservices                     │
│  • Shared exception handling logic                                         │
│  • Reusable utilities (IdGenerator)                                        │
│  • Single place to update common code                                      │
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

### 5.2 Test Endpoints

```powershell
# Test 1: Register a new user (CUSTOMER role)
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\",\"password\":\"Password123\",\"fullName\":\"John Doe\",\"phone\":\"+1234567890\",\"role\":\"CUSTOMER\"}"

# Expected: 201 Created
# {
#   "success": true,
#   "data": {
#     "accessToken": "eyJhbGci...",
#     "refreshToken": "eyJhbGci...",
#     "tokenType": "Bearer",
#     "expiresIn": 900,
#     "user": {
#       "userId": "a1B2c3D4e5",
#       "email": "test@example.com",
#       "fullName": "John Doe",
#       "role": "CUSTOMER"
#     }
#   },
#   "timestamp": "2024-01-15T10:30:00Z"
# }

# Test 2: Login
curl -X POST http://localhost:8081/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\",\"password\":\"Password123\"}"

# Expected: 200 OK with same response structure

# Test 3: Invalid password
curl -X POST http://localhost:8081/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\",\"password\":\"wrongpassword\"}"

# Expected: 401 Unauthorized
# {
#   "success": false,
#   "error": {
#     "code": "INVALID_CREDENTIALS",
#     "message": "Email or password is incorrect"
#   },
#   "timestamp": "2024-01-15T10:30:00Z"
# }

# Test 4: Validation error (invalid email)
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"notanemail\",\"password\":\"short\",\"fullName\":\"\",\"role\":\"CUSTOMER\"}"

# Expected: 400 Bad Request with validation errors

# Test 5: Duplicate email
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"test@example.com\",\"password\":\"Password123\",\"fullName\":\"Jane Doe\",\"role\":\"MERCHANT\"}"

# Expected: 409 Conflict
# {
#   "success": false,
#   "error": {
#     "code": "DUPLICATE_EMAIL",
#     "message": "A user with email 'test@example.com' already exists"
#   }
# }

# Test 6: Invalid role
curl -X POST http://localhost:8081/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"new@example.com\",\"password\":\"Password123\",\"fullName\":\"Test User\",\"role\":\"INVALID\"}"

# Expected: 400 Bad Request
# {
#   "success": false,
#   "error": {
#     "code": "INVALID_ROLE",
#     "message": "Role must be CUSTOMER or MERCHANT"
#   }
# }
```

---

## 6. File Structure

After completing this part:

```
identity-service/
├── src/main/java/com/payflow/identity/
│   ├── config/
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   └── AuthController.java     ← NEW: REST endpoints
│   ├── dto/
│   │   ├── AuthResponse.java       (with nested UserInfo)
│   │   ├── ProfileResponse.java    ← NEW: Profile endpoint response
│   │   ├── LoginRequest.java
│   │   └── RegisterRequest.java
│   ├── model/
│   │   └── User.java
│   ├── repository/
│   │   └── UserRepository.java
│   └── service/
│       ├── AuthService.java
│       └── JwtService.java

common-lib/ (already exists, provides shared components)
├── com/payflow/common/
│   ├── dto/
│   │   └── ApiResponse.java        ← Used for response wrapping
│   └── exception/
│       ├── GlobalExceptionHandler.java  ← Handles all exceptions
│       ├── PayflowException.java
│       └── DuplicateResourceException.java
```


**Important Differences from Generic Tutorials:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PAYFLOW-SPECIFIC DESIGN CHOICES                           │
│                                                                              │
│  WHAT GENERIC TUTORIALS SHOW     │ WHAT PAYFLOW USES                       │
│  ─────────────────────────────────────────────────────────────────────────  │
│  Returns raw DTO                  │ Returns ApiResponse<DTO>                │
│  Custom GlobalExceptionHandler    │ Uses GlobalExceptionHandler from        │
│    in each service                │   common-lib (shared)                   │
│  Custom exception classes         │ Uses exceptions from common-lib         │
│  /refresh, /logout, /me endpoints │ Only /register and /login               │
│  firstName, lastName              │ fullName single field                   │
│  Default role MERCHANT            │ Role from request (CUSTOMER/MERCHANT)   │
│  Manual logging                   │ @RequiredArgsConstructor                │
│  No Swagger annotations           │ @Tag, @Operation, @ApiResponses         │
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
│  ✅ REST Controllers                                                        │
│     • @RestController for JSON endpoints                                   │
│     • @RequestMapping for base path                                        │
│     • @PostMapping for HTTP POST methods                                  │
│     • @Valid for input validation                                         │
│     • @RequiredArgsConstructor for dependency injection                   │
│                                                                              │
│  ✅ API Response Wrapping                                                   │
│     • All responses wrapped in ApiResponse<T>                             │
│     • ApiResponse.success(data) for success                               │
│     • Consistent format across all services                               │
│                                                                              │
│  ✅ Swagger/OpenAPI Annotations                                            │
│     • @Tag for grouping endpoints                                         │
│     • @Operation for endpoint description                                 │
│     • @ApiResponses for documenting status codes                         │
│                                                                              │
│  ✅ Common-Lib Integration                                                 │
│     • ApiResponse from common-lib                                         │
│     • GlobalExceptionHandler handles all errors                           │
│     • No try-catch needed in controller                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Q&A / Troubleshooting

### Q1: "Could not find class ApiResponse"

**Cause:** common-lib dependency not included or not built.

**Fix:**
```powershell
# Build common-lib first
cd common-lib
mvn clean install

# Then build identity-service
cd ../identity-service
mvn clean compile
```

### Q2: Validation not working

**Cause:** Missing spring-boot-starter-validation dependency.

**Fix:** It should be inherited from parent POM. Check pom.xml includes:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### Q3: 403 Forbidden on /v1/auth/* endpoints

**Cause:** Spring Security not configured to permit auth endpoints.

**Fix:** Verify SecurityConfig has:
```java
.requestMatchers("/v1/auth/**").permitAll()
```

### Q4: Swagger UI not showing

**Cause:** springdoc-openapi dependency missing.

**Fix:** Check identity-service pom.xml includes:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS                                            │
│                                                                              │
│  Swagger UI                                                                 │
│  ─────────                                                                  │
│  Access at http://localhost:8081/swagger-ui.html                           │
│  Part 08 covers additional Swagger configuration.                          │
│                                                                              │
│  Additional Endpoints (Future)                                              │
│  ─────────────────────────────                                              │
│  • POST /v1/auth/refresh - Token refresh                                   │
│  • POST /v1/auth/logout - Logout                                           │
│  • GET /v1/auth/me - Get current user                                      │
│  These can be added when needed.                                           │
│                                                                              │
│  Rate Limiting                                                              │
│  ─────────────                                                              │
│  Handled at API Gateway level (Part 03).                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ✅ Part 07 COMPLETE: Identity Controllers                                  │
│                                                                              │
│  NEXT: Part 08 - Identity Swagger                                           │
│  ─────────────────────────────────                                          │
│  Customize OpenAPI documentation for your endpoints.                        │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  IDENTITY SERVICE BUILD PROGRESS                                    │   │
│  │                                                                      │   │
│  │  Part 04: Setup ✅                                                  │   │
│  │  Part 05: Database ✅      - Entity, migration, repository          │   │
│  │  Part 06: JWT Auth ✅      - JwtService, AuthService, Security      │   │
│  │  Part 07: Controllers ✅   - REST endpoints with ApiResponse        │   │
│  │  Part 08: Swagger          - API docs                               │   │
│  │  Part 09: Testing          - Tests                                  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Continue to: part-08-identity-swagger.md                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 07 Complete!** 🎉

You now have:
- AuthController with /register and /login endpoints
- Swagger annotations for API documentation
- Integration with common-lib (ApiResponse, GlobalExceptionHandler)
- Clean, thin controller that delegates to AuthService
