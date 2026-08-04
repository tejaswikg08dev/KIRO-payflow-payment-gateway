# Hands-On Guide — Phase 4 Part 4: Swagger Documentation & Testing

## Goal

By the end of Part 4, you will have:
- Swagger UI fully documented with descriptions and examples
- Postman collection with register + login requests saved
- Unit test for AuthService (register flow)
- identity-service Phase 4 COMPLETE
- Git commit

## Prerequisites

- Part 3 completed (register and login work via curl)
- Service running on port 8081

---

## Step 4.1: Access Swagger UI

Open browser: **http://localhost:8081/swagger-ui.html**

You should see:
```
PayFlow Identity Service API (1.0)
User registration, login, and JWT token management

Authentication
  POST /v1/auth/register    Register a new user
  POST /v1/auth/login       Login with email and password
```

Click any endpoint → "Try it out" → paste JSON → "Execute" → see response.

This is **auto-generated** from our @Operation and @ApiResponses annotations.
No separate documentation file needed!

---

## Step 4.2: Export OpenAPI Spec

The machine-readable API specification (for Postman import, code generation):

```cmd
curl http://localhost:8081/v3/api-docs
```

This returns a JSON file that describes ALL endpoints, request schemas,
response schemas, error codes — everything Swagger UI shows, but as JSON.

**Save it:**
```cmd
curl http://localhost:8081/v3/api-docs > docs/postman/identity-service-openapi.json
```

---

## Step 4.3: Create Postman Collection

**Step 1:** Open Postman → Import → paste URL: `http://localhost:8081/v3/api-docs`

Postman auto-creates a collection with both endpoints!

**Step 2:** Or create manually:

```
Collection: PayFlow Identity Service
├── POST Register
│   URL: http://localhost:8081/v1/auth/register
│   Body (JSON):
│   {
│     "email": "test@example.com",
│     "password": "TestPass123",
│     "fullName": "Test User",
│     "phone": "+919876543210",
│     "role": "MERCHANT"
│   }
│
└── POST Login
    URL: http://localhost:8081/v1/auth/login
    Body (JSON):
    {
      "email": "test@example.com",
      "password": "TestPass123"
    }
```

**Step 3:** Add environment variable extraction:

In the Register request → Tests tab:
```javascript
if (pm.response.code === 201) {
    var data = pm.response.json().data;
    pm.environment.set("access_token", data.accessToken);
    pm.environment.set("user_id", data.user.userId);
}
```

Now `{{access_token}}` is available for other requests!

---

## Step 4.4: Write Unit Test for AuthService

**Create file:** `identity-service/src/test/java/com/payflow/identity/service/AuthServiceTest.java`

```java
package com.payflow.identity.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.model.User;
import com.payflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
// Initializes @Mock and @InjectMocks annotations
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    // Creates a fake UserRepository (doesn't touch real DB)

    @Mock
    private PasswordEncoder passwordEncoder;
    // Creates a fake PasswordEncoder

    @Mock
    private JwtService jwtService;
    // Creates a fake JwtService

    @InjectMocks
    private AuthService authService;
    // Creates real AuthService but injects the mocks above

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest(
            "test@example.com", "SecureP@ss123",
            "Test User", "+919876543210", "MERCHANT"
        );
    }

    @Test
    @DisplayName("Should register user successfully with valid input")
    void register_ValidInput_ReturnsAuthResponse() {
        // ARRANGE: Set up mock behaviors
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecureP@ss123")).thenReturn("$2a$12$hashedValue");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("fake-access-token");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("fake-refresh-token");

        // ACT: Call the method we're testing
        AuthResponse response = authService.register(validRequest);

        // ASSERT: Verify the result
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("fake-access-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getUser().getRole()).isEqualTo("MERCHANT");

        // Verify interactions
        verify(userRepository).existsByEmail("test@example.com");
        verify(passwordEncoder).encode("SecureP@ss123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email exists")
    void register_DuplicateEmail_ThrowsException() {
        // ARRANGE
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // ACT & ASSERT
        assertThatThrownBy(() -> authService.register(validRequest))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("already exists");

        // Verify: save was NEVER called (registration aborted)
        verify(userRepository, never()).save(any());
    }
}
```

**Run the test:**
```cmd
cd identity-service
mvn test -Dtest=AuthServiceTest
```

**Expected:** 2 tests pass ✓

---

## Step 4.5: Git Commit

```cmd
git add identity-service/src/test/
git add docs/phase4-part4-swagger-and-testing.md
git commit -m "Phase 4 Part 4: Swagger docs, Postman setup, unit test for AuthService"
```

---

## Phase 4 Complete! 🎉

| Part | What We Built |
|------|--------------|
| Part 1 | pom.xml, Flyway migration, User entity, application.yml |
| Part 2 | JwtService (token create/validate), AuthService (register/login) |
| Part 3 | AuthController (REST endpoints), SecurityConfig (public/protected URLs) |
| Part 4 | Swagger UI, Postman collection, unit test |

**Identity Service is fully working.** You can:
- Register users: POST /v1/auth/register
- Login users: POST /v1/auth/login
- See API docs: http://localhost:8081/swagger-ui.html
- JWT tokens are generated and can be used for other services

---

## Next Step

→ Move to **Phase 5: Merchant Service**
→ Start with **`phase5-part1-project-setup-and-database.md`**
