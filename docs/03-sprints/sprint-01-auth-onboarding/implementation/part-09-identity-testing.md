# Sprint 1, Part 09: Identity Service Testing

**Duration:** 2-3 hours  
**Prerequisites:** Parts 04-08 completed, Identity Service fully implemented

---

## 1. What We're Building

In this part, you'll write **unit and integration tests** for the Identity Service.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     TESTING PYRAMID                                          │
│                                                                              │
│                        /\                                                   │
│                       /  \                                                  │
│                      / E2E\     ← Few, slow, test entire flow              │
│                     /──────\                                                │
│                    /        \                                               │
│                   /Integration\  ← Some, test components together          │
│                  /────────────\                                             │
│                 /              \                                            │
│                /   Unit Tests   \ ← Many, fast, test single units          │
│               /──────────────────\                                          │
│                                                                              │
│  We'll create:                                                              │
│  ─────────────                                                              │
│  • Unit Tests: JwtService, AuthService (mocked dependencies)               │
│  • Integration Tests: Controller (with MockMvc)                            │
│                                                                              │
│  Test Coverage Goals:                                                       │
│  ───────────────────                                                        │
│  • Happy paths (successful operations)                                      │
│  • Error cases (invalid input, not found, etc.)                            │
│  • Edge cases (boundary conditions)                                        │
│  • Security scenarios (account status, token validation)                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 JUnit 5 and Spring Boot Test

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TESTING ANNOTATIONS                                       │
│                                                                              │
│  @Test                                                                       │
│  ─────                                                                      │
│  Marks a method as a test case                                             │
│                                                                              │
│  @SpringBootTest                                                            │
│  ───────────────                                                            │
│  Loads full Spring context (for integration tests)                         │
│  Slow but tests real behavior                                              │
│                                                                              │
│  @WebMvcTest                                                                │
│  ───────────                                                                │
│  Loads only web layer (controllers)                                        │
│  Faster, good for controller tests                                         │
│                                                                              │
│  @DataJpaTest                                                               │
│  ────────────                                                               │
│  Loads only JPA components (repositories)                                  │
│  Uses embedded H2 database by default                                      │
│                                                                              │
│  @MockBean                                                                  │
│  ─────────                                                                  │
│  Creates a mock of a Spring bean                                           │
│  Replaces real bean in context                                             │
│                                                                              │
│  @BeforeEach / @AfterEach                                                  │
│  ────────────────────────                                                   │
│  Runs before/after each test method                                        │
│  Good for setup and cleanup                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Mockito Basics

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MOCKITO PATTERNS                                          │
│                                                                              │
│  Creating mocks:                                                            │
│  ───────────────                                                            │
│  @Mock                                                                      │
│  private UserRepository userRepository;                                    │
│                                                                              │
│  Stubbing (define behavior):                                                │
│  ───────────────────────────                                                │
│  when(userRepository.findByEmail("test@example.com"))                      │
│      .thenReturn(Optional.of(user));                                       │
│                                                                              │
│  Verification (check method was called):                                    │
│  ─────────────────────────────────────                                      │
│  verify(userRepository).save(any(User.class));                             │
│  verify(userRepository, times(1)).findByEmail(anyString());                │
│  verify(userRepository, never()).delete(any());                            │
│                                                                              │
│  Argument capturing:                                                        │
│  ──────────────────                                                         │
│  ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);        │
│  verify(userRepository).save(captor.capture());                            │
│  User savedUser = captor.getValue();                                       │
│  assertEquals("test@example.com", savedUser.getEmail());                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Test dependencies are already included in the parent `pom.xml`:

```xml
<!-- In root pom.xml - already present -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

This includes:
- JUnit 5 (Jupiter)
- Mockito
- AssertJ
- Hamcrest
- Spring Test / Spring Boot Test

```powershell
# Verify build works
cd identity-service
mvn clean compile
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Test Configuration

**File: `identity-service/src/test/resources/application-test.yml`**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# TEST CONFIGURATION
# Uses H2 in-memory database for fast tests
# ═══════════════════════════════════════════════════════════════════════════

spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

  flyway:
    enabled: false  # Don't run migrations in tests

# JWT configuration for tests (HMAC secret)
jwt:
  secret: test-secret-key-for-unit-tests-must-be-at-least-256-bits-long
  access-token-expiry: 900000   # 15 minutes
  refresh-token-expiry: 604800000  # 7 days

# Disable Eureka for tests
eureka:
  client:
    enabled: false
```

**Note:** We use HMAC secret (not RSA keys) because that's what the actual `JwtService` uses.

---

### Step 4.2: Add H2 Test Dependency

Add H2 database for in-memory testing.

**File: `identity-service/pom.xml`** (add to dependencies)

```xml
<!-- H2 Database for testing -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

---

### Step 4.3: Create JwtService Unit Tests

**File: `identity-service/src/test/java/com/payflow/identity/service/JwtServiceTest.java`**

```java
package com.payflow.identity.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService.
 * Tests token generation and validation with HMAC signing.
 */
class JwtServiceTest {

    private JwtService jwtService;

    // Test data
    private static final String USER_ID = "usr_abc12345";
    private static final String EMAIL = "test@example.com";
    private static final String ROLE = "CUSTOMER";
    private static final String SECRET = "test-secret-key-for-unit-tests-must-be-at-least-256-bits-long";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inject test values using reflection
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", 900000L);  // 15 min
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiry", 604800000L);  // 7 days
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACCESS TOKEN TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Access Token Tests")
    class AccessTokenTests {

        @Test
        @DisplayName("Should generate valid access token")
        void generateAccessToken_Success() {
            // Act
            String token = jwtService.generateAccessToken(USER_ID, EMAIL, ROLE);

            // Assert
            assertNotNull(token);
            assertFalse(token.isEmpty());

            // Validate token
            Claims claims = jwtService.validateToken(token);
            assertNotNull(claims);
            assertEquals(USER_ID, claims.get("userId", String.class));
            assertEquals(EMAIL, claims.get("email", String.class));
            assertEquals(ROLE, claims.get("role", String.class));
            assertEquals("ACCESS", claims.get("type", String.class));
        }

        @Test
        @DisplayName("Should extract userId from access token")
        void extractUserId_Success() {
            // Arrange
            String token = jwtService.generateAccessToken(USER_ID, EMAIL, ROLE);

            // Act
            String extractedUserId = jwtService.extractUserId(token);

            // Assert
            assertEquals(USER_ID, extractedUserId);
        }

        @Test
        @DisplayName("Access token should not be expired immediately")
        void accessToken_ShouldNotBeExpiredImmediately() {
            // Arrange
            String token = jwtService.generateAccessToken(USER_ID, EMAIL, ROLE);

            // Act
            boolean expired = jwtService.isTokenExpired(token);

            // Assert
            assertFalse(expired);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REFRESH TOKEN TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should generate valid refresh token")
        void generateRefreshToken_Success() {
            // Act
            String token = jwtService.generateRefreshToken(USER_ID, EMAIL);

            // Assert
            assertNotNull(token);
            Claims claims = jwtService.validateToken(token);
            assertEquals(USER_ID, claims.get("userId", String.class));
            assertEquals("REFRESH", claims.get("type", String.class));
            // Refresh token should NOT have role
            assertNull(claims.get("role", String.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOKEN VALIDATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should return null for invalid token")
        void validateToken_InvalidToken_ReturnsNull() {
            // Act
            Claims claims = jwtService.validateToken("invalid.token.here");

            // Assert
            assertNull(claims);
        }

        @Test
        @DisplayName("Should return null for tampered token")
        void validateToken_TamperedToken_ReturnsNull() {
            // Arrange
            String token = jwtService.generateAccessToken(USER_ID, EMAIL, ROLE);
            String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

            // Act
            Claims claims = jwtService.validateToken(tamperedToken);

            // Assert
            assertNull(claims);
        }
    }
}
```

---

### Step 4.4: Create AuthService Unit Tests

**File: `identity-service/src/test/java/com/payflow/identity/service/AuthServiceTest.java`**

```java
package com.payflow.identity.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.PayflowException;
import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.model.User;
import com.payflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 *
 * @ExtendWith(MockitoExtension.class) enables Mockito annotations.
 * Dependencies are mocked, so we test AuthService logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // Create test user (matching actual User model)
        testUser = User.builder()
                .id("usr_abc12345")
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .fullName("John Doe")
                .phone("+1234567890")
                .role(User.Role.CUSTOMER)
                .emailVerified(false)
                .status(User.UserStatus.ACTIVE)
                .build();

        // Create register request (matching actual RegisterRequest DTO)
        registerRequest = new RegisterRequest(
                "test@example.com",
                "Password123",
                "John Doe",
                "+1234567890",
                "CUSTOMER"
        );

        // Create login request
        loginRequest = new LoginRequest("test@example.com", "Password123");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REGISTER TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register new user successfully")
        void register_Success() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(jwtService.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("accessToken");
            when(jwtService.generateRefreshToken(anyString(), anyString()))
                    .thenReturn("refreshToken");

            // Act
            AuthResponse response = authService.register(registerRequest);

            // Assert
            assertNotNull(response);
            assertEquals("accessToken", response.getAccessToken());
            assertEquals("refreshToken", response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());

            // Verify password was hashed
            verify(passwordEncoder).encode("Password123");

            // Verify user was saved with correct data
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals("test@example.com", savedUser.getEmail());
            assertEquals("John Doe", savedUser.getFullName());
            assertEquals(User.Role.CUSTOMER, savedUser.getRole());
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when email exists")
        void register_EmailExists_ThrowsException() {
            // Arrange
            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            // Act & Assert
            assertThrows(DuplicateResourceException.class,
                    () -> authService.register(registerRequest));

            // Verify user was never saved
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PayflowException for invalid role")
        void register_InvalidRole_ThrowsException() {
            // Arrange
            RegisterRequest badRequest = new RegisterRequest(
                    "test@example.com", "Password123", "John", null, "INVALID_ROLE"
            );
            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            // Act & Assert
            PayflowException ex = assertThrows(PayflowException.class,
                    () -> authService.register(badRequest));
            assertEquals("INVALID_ROLE", ex.getCode());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtService.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn("accessToken");
            when(jwtService.generateRefreshToken(anyString(), anyString()))
                    .thenReturn("refreshToken");

            // Act
            AuthResponse response = authService.login(loginRequest);

            // Assert
            assertNotNull(response);
            assertEquals("accessToken", response.getAccessToken());
            assertEquals("refreshToken", response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());

            // Verify user was saved (to update lastLoginAt)
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw PayflowException when user not found")
        void login_UserNotFound_ThrowsException() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            // Act & Assert
            PayflowException ex = assertThrows(PayflowException.class,
                    () -> authService.login(loginRequest));
            assertEquals("INVALID_CREDENTIALS", ex.getCode());
        }

        @Test
        @DisplayName("Should throw PayflowException when password is wrong")
        void login_WrongPassword_ThrowsException() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            // Act & Assert
            PayflowException ex = assertThrows(PayflowException.class,
                    () -> authService.login(loginRequest));
            assertEquals("INVALID_CREDENTIALS", ex.getCode());
        }

        @Test
        @DisplayName("Should throw PayflowException when account is suspended")
        void login_AccountSuspended_ThrowsException() {
            // Arrange
            testUser.setStatus(User.UserStatus.SUSPENDED);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            // Act & Assert
            PayflowException ex = assertThrows(PayflowException.class,
                    () -> authService.login(loginRequest));
            assertEquals("ACCOUNT_SUSPENDED", ex.getCode());
        }
    }
}
```

---

### Step 4.5: Create Controller Integration Tests

**File: `identity-service/src/test/java/com/payflow/identity/controller/AuthControllerTest.java`**

```java
package com.payflow.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 *
 * @SpringBootTest loads application context
 * @AutoConfigureMockMvc provides MockMvc for HTTP testing
 * @ActiveProfiles("test") uses application-test.yml
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;  // Mock service to isolate controller

    // ─────────────────────────────────────────────────────────────────────────
    // REGISTER ENDPOINT TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/auth/register - Success returns 201")
    void register_Success() throws Exception {
        // Arrange
        RegisterRequest request = new RegisterRequest(
                "test@example.com", "Password123", "John Doe", "+1234567890", "CUSTOMER"
        );

        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponse.UserInfo.builder()
                        .userId("usr_abc12345")
                        .email("test@example.com")
                        .fullName("John Doe")
                        .role("CUSTOMER")
                        .build())
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /v1/auth/register - Validation error returns 400")
    void register_ValidationError_Returns400() throws Exception {
        // Arrange - invalid email and short password
        RegisterRequest request = new RegisterRequest(
                "notanemail", "short", "", null, ""
        );

        // Act & Assert
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").exists());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN ENDPOINT TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/auth/login - Success returns 200")
    void login_Success() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("test@example.com", "Password123");

        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponse.UserInfo.builder()
                        .userId("usr_abc12345")
                        .email("test@example.com")
                        .fullName("John Doe")
                        .role("CUSTOMER")
                        .build())
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /v1/auth/login - Validation error returns 400")
    void login_ValidationError_Returns400() throws Exception {
        // Arrange - empty email and password
        LoginRequest request = new LoginRequest("", "");

        // Act & Assert
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
```

---

## 5. Verification

### 5.1 Run All Tests

```powershell
cd identity-service

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JwtServiceTest

# Run specific test method
mvn test -Dtest=AuthServiceTest#register_Success

# Run tests with verbose output
mvn test -Dtest=AuthServiceTest -X
```

**Expected output:**
```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 5.2 Run Tests with Coverage

Add JaCoCo plugin to `identity-service/pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        
        <!-- JaCoCo for test coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Then run:
```powershell
mvn test jacoco:report
```

Open: `target/site/jacoco/index.html` to view coverage report.

---

## 6. File Structure

After completing this part:

```
identity-service/
├── src/
│   ├── main/java/com/payflow/identity/
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── dto/
│   │   │   ├── AuthResponse.java
│   │   │   ├── LoginRequest.java
│   │   │   └── RegisterRequest.java
│   │   ├── model/
│   │   │   └── User.java
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   └── service/
│   │       ├── AuthService.java
│   │       └── JwtService.java
│   │
│   └── test/
│       ├── java/com/payflow/identity/
│       │   ├── controller/
│       │   │   └── AuthControllerTest.java
│       │   └── service/
│       │       ├── AuthServiceTest.java
│       │       └── JwtServiceTest.java
│       │
│       └── resources/
│           └── application-test.yml
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TESTING KNOWLEDGE SUMMARY                                 │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐│
│  │                      TEST TYPES COMPARISON                             ││
│  │                                                                        ││
│  │  Unit Tests                      Integration Tests                     ││
│  │  ───────────                     ─────────────────                     ││
│  │  • Test single class              • Test components together           ││
│  │  • Mock dependencies              • Real Spring context                ││
│  │  • Very fast (ms)                 • Slower (seconds)                   ││
│  │  • Many tests                     • Fewer tests                        ││
│  │  • @ExtendWith(Mockito)           • @SpringBootTest                    ││
│  │  • No database needed             • Uses H2 in-memory DB               ││
│  │                                                                        ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Concept | What You Learned |
|---------|------------------|
| **@ExtendWith(MockitoExtension)** | Enables Mockito mocks in JUnit 5 tests |
| **@Mock** | Creates a mock object (fake implementation) |
| **@InjectMocks** | Injects mocks into the class under test |
| **when().thenReturn()** | Defines mock behavior (stubbing) |
| **verify()** | Checks that a method was called |
| **@SpringBootTest** | Loads full application context |
| **@ActiveProfiles("test")** | Uses test-specific configuration |
| **@MockBean** | Mocks a Spring bean in the context |
| **MockMvc** | Simulates HTTP requests without server |
| **@Nested** | Groups related tests together |
| **@DisplayName** | Human-readable test names |
| **ArgumentCaptor** | Captures arguments passed to mocks |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TEST NAMING CONVENTION                                    │
│                                                                              │
│  Pattern: methodName_scenario_expectedResult                                │
│                                                                              │
│  Examples:                                                                   │
│  ─────────                                                                  │
│  • register_Success()                              → Happy path             │
│  • register_EmailExists_ThrowsException()          → Error case             │
│  • login_AccountSuspended_ThrowsException()        → Edge case              │
│  • login_WrongPassword_ThrowsException()           → Validation             │
│                                                                              │
│  Benefits:                                                                   │
│  ─────────                                                                  │
│  • Clear what's being tested                                                │
│  • Easy to find failing test                                                │
│  • Self-documenting                                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Common Issues & Solutions

### Issue: Tests fail with "No qualifying bean"

```
Error: No qualifying bean of type 'com.payflow.identity.service.JwtService' available
```

**Solution:**
```java
// Option 1: Mock the bean
@MockBean
private JwtService jwtService;

// Option 2: Use @SpringBootTest with test profile
@SpringBootTest
@ActiveProfiles("test")
class MyTest { ... }
```

---

### Issue: H2 database schema errors

```
Error: Table "USERS" not found; SQL statement
```

**Cause:** Schema not created or wrong dialect.

**Solution:**
```yaml
# In application-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # Hibernate creates tables
  flyway:
    enabled: false  # Disable Flyway in tests
```

---

### Issue: Tests fail with "Could not autowire"

```
Error: Could not autowire. No beans of 'AuthService' type found.
```

**Cause:** Component scanning not finding beans.

**Solution:** Ensure `@SpringBootTest` loads the full context:
```java
@SpringBootTest(classes = IdentityServiceApplication.class)
class MyTest { ... }
```

---

### Issue: JWT validation fails in tests

```
Error: JWT signature does not match
```

**Cause:** Test config uses different secret than JwtService expects.

**Solution:** Use `ReflectionTestUtils` to inject test values:
```java
@BeforeEach
void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "secretKey", "test-secret...");
    ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", 900000L);
}
```

---

## 9. Testing Best Practices

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TESTING BEST PRACTICES                                    │
│                                                                              │
│  1. AAA Pattern                                                             │
│  ──────────────                                                             │
│  // Arrange - Set up test data and mocks                                   │
│  when(repository.findByEmail(email)).thenReturn(user);                     │
│                                                                              │
│  // Act - Execute the method under test                                    │
│  AuthResponse result = authService.login(request);                         │
│                                                                              │
│  // Assert - Verify the result                                             │
│  assertEquals("token", result.getAccessToken());                           │
│                                                                              │
│  2. One Assertion Per Concept                                              │
│  ────────────────────────────                                               │
│  Don't test multiple behaviors in one test                                 │
│                                                                              │
│  3. Test Edge Cases                                                        │
│  ─────────────────                                                          │
│  • Null inputs                                                             │
│  • Empty strings                                                           │
│  • Boundary values                                                         │
│  • Error conditions                                                        │
│                                                                              │
│  4. Use Descriptive Names                                                  │
│  ────────────────────────                                                   │
│  @DisplayName("Should throw exception when email already exists")          │
│                                                                              │
│  5. Keep Tests Independent                                                 │
│  ────────────────────────                                                   │
│  Each test should work alone, don't depend on order                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. What's Next?

In the next part, we'll:
- Set up the **Merchant Service** microservice
- Create merchant registration and management APIs
- Connect Identity Service with Merchant Service

**Next:** [Part 10: Merchant Service Setup](part-10-merchant-service-setup.md)

---

## Quick Reference

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    QUICK REFERENCE CARD                                      │
│                                                                              │
│  Run Tests:                                                                 │
│  mvn test                          # All tests                              │
│  mvn test -Dtest=JwtServiceTest    # Specific class                        │
│  mvn test jacoco:report            # With coverage                         │
│                                                                              │
│  Test Annotations:                                                          │
│  @Test                             # Mark test method                       │
│  @BeforeEach                       # Setup before each test                │
│  @Nested                           # Group related tests                   │
│  @DisplayName("...")               # Readable test name                    │
│                                                                              │
│  Mock Annotations:                                                          │
│  @Mock                             # Create mock object                    │
│  @InjectMocks                      # Inject mocks into target              │
│  @MockBean                         # Mock Spring bean                      │
│                                                                              │
│  Mockito Methods:                                                          │
│  when(...).thenReturn(...)         # Define mock behavior                  │
│  verify(mock).method(...)          # Check method was called               │
│  verify(mock, never()).method()    # Check method NOT called               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```
