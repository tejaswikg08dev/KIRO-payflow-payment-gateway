# Sprint 1, Part 08: Identity Service Swagger/OpenAPI

**Duration:** 30-45 minutes  
**Prerequisites:** Part 07 completed, REST controllers implemented

---

## 1. What We're Building

In this part, you'll verify and understand the **OpenAPI/Swagger documentation** in your Identity Service.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SWAGGER UI BENEFITS                                      │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    SWAGGER UI                                        │   │
│  │                    http://localhost:8081/swagger-ui.html            │   │
│  ├─────────────────────────────────────────────────────────────────────┤   │
│  │                                                                      │   │
│  │  Identity Service API                                               │   │
│  │  ─────────────────────                                              │   │
│  │                                                                      │   │
│  │  Authentication                                                     │   │
│  │  ├── POST /v1/auth/register    Register a new user                 │   │
│  │  └── POST /v1/auth/login       Login with email and password       │   │
│  │                                                                      │   │
│  │  [Try it out]  ← Interactive testing!                              │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  WHY SWAGGER?                                                               │
│  ────────────                                                               │
│  ✅ Auto-generated documentation from code                                 │
│  ✅ Interactive API testing (no curl needed!)                              │
│  ✅ Client SDK generation (TypeScript, Java, Python)                       │
│  ✅ Keeps documentation in sync with code                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 OpenAPI vs Swagger

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OPENAPI vs SWAGGER                                        │
│                                                                              │
│  OpenAPI Specification (OAS):                                               │
│  ─────────────────────────────                                              │
│  • Standard format for describing REST APIs                                 │
│  • YAML or JSON format                                                      │
│  • Version 3.0+ is current standard                                        │
│  • Language-agnostic                                                        │
│                                                                              │
│  Swagger:                                                                    │
│  ─────────                                                                  │
│  • Set of tools that implement OpenAPI                                     │
│  • Swagger UI: Interactive documentation                                   │
│  • Swagger Codegen: Generate client SDKs                                   │
│                                                                              │
│  SpringDoc:                                                                  │
│  ──────────                                                                 │
│  • Library for Spring Boot + OpenAPI 3.0                                   │
│  • Auto-generates OpenAPI spec from code                                   │
│  • Includes Swagger UI                                                     │
│  • Replaces older springfox library                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 OpenAPI Annotations (Already Used)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    KEY ANNOTATIONS                                           │
│                                                                              │
│  @Tag(name = "Authentication")                                              │
│  ─────────────────────────────                                              │
│  Groups endpoints in Swagger UI                                             │
│                                                                              │
│  @Operation(summary = "...", description = "...")                          │
│  ───────────────────────────────────────────────                            │
│  Describes what an endpoint does                                           │
│                                                                              │
│  @ApiResponses({ @ApiResponse(...) })                                      │
│  ──────────────────────────────────                                         │
│  Documents possible HTTP responses                                          │
│                                                                              │
│  These annotations are ALREADY in our AuthController!                       │
│  (Added in Part 07)                                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. What's Already Configured

### 3.1 SpringDoc Dependency (in pom.xml)

The dependency was already added:

```xml
<!-- Swagger / OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

### 3.2 application.yml Configuration

**File: `identity-service/src/main/resources/application.yml`**

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

### 3.3 Controller Annotations (from Part 07)

**AuthController already has these annotations:**

```java
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, and token management")
public class AuthController {

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT tokens")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(...) { ... }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password", description = "Validates credentials and returns JWT tokens")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(...) { ... }
}
```

---

## 4. Verification


### 4.1 Start Identity Service

```powershell
cd identity-service
mvn spring-boot:run
```

### 4.2 Access Swagger UI

Open in browser: **http://localhost:8081/swagger-ui.html**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Expected Swagger UI:                                                        │
│                                                                              │
│  Identity Service API                                                       │
│  ─────────────────────                                                      │
│                                                                              │
│  Authentication                                                              │
│  User registration, login, and token management                             │
│                                                                              │
│  ├── POST /v1/auth/register                                                │
│  │   Summary: Register a new user                                          │
│  │   Description: Creates a new user account and returns JWT tokens        │
│  │   [Try it out]                                                          │
│  │                                                                          │
│  └── POST /v1/auth/login                                                   │
│      Summary: Login with email and password                                │
│      Description: Validates credentials and returns JWT tokens             │
│      [Try it out]                                                          │
│                                                                              │
│  Schemas                                                                     │
│  ├── RegisterRequest                                                        │
│  ├── LoginRequest                                                           │
│  ├── AuthResponse                                                           │
│  └── ApiResponse                                                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Test Registration via Swagger UI

1. Click **POST /v1/auth/register**
2. Click **Try it out**
3. Enter test data:
   ```json
   {
     "email": "test@example.com",
     "password": "Password123",
     "fullName": "John Doe",
     "phone": "+1234567890",
     "role": "CUSTOMER"
   }
   ```
4. Click **Execute**
5. See the response with tokens

### 4.4 Test Login via Swagger UI

1. Click **POST /v1/auth/login**
2. Click **Try it out**
3. Enter credentials:
   ```json
   {
     "email": "test@example.com",
     "password": "Password123"
   }
   ```
4. Click **Execute**
5. See the response with tokens

### 4.5 Access OpenAPI Spec

```powershell
# Get OpenAPI JSON spec
curl http://localhost:8081/v3/api-docs

# This can be used to generate client SDKs
```

---

## 5. File Structure

The Swagger/OpenAPI functionality requires:

```
identity-service/
├── pom.xml                           ← Has springdoc dependency
├── src/main/java/com/payflow/identity/
│   └── controller/
│       └── AuthController.java       ← Has @Tag, @Operation, @ApiResponses
└── src/main/resources/
    └── application.yml               ← Has springdoc config
```

**Note:** No separate OpenApiConfig.java is needed for basic functionality.
SpringDoc auto-generates the OpenAPI spec from controller annotations.

---

## 6. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ SpringDoc Auto-Configuration                                            │
│     • Just add dependency - Swagger UI works automatically                 │
│     • No custom config class needed for basic setup                        │
│                                                                              │
│  ✅ Key Annotations (from Part 07)                                         │
│     • @Tag - Groups endpoints                                              │
│     • @Operation - Describes endpoint                                      │
│     • @ApiResponses - Documents HTTP responses                             │
│                                                                              │
│  ✅ Swagger UI Features                                                     │
│     • Interactive API testing                                              │
│     • Request body examples                                                │
│     • Response visualization                                               │
│                                                                              │
│  ✅ URLs                                                                    │
│     • Swagger UI: /swagger-ui.html                                         │
│     • OpenAPI spec: /v3/api-docs                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Q&A / Troubleshooting

### Q1: Swagger UI returns 404

**Cause:** SpringDoc not detecting controllers.

**Fix:** Ensure springdoc dependency is in pom.xml and rebuild:
```powershell
mvn clean package -DskipTests
```

### Q2: Endpoints not showing

**Cause:** Controllers not being scanned.

**Fix:** Add to application.yml:
```yaml
springdoc:
  packages-to-scan: com.payflow.identity.controller
```

### Q3: 403 Forbidden when accessing Swagger UI

**Cause:** Spring Security blocking Swagger endpoints.

**Fix:** Ensure SecurityConfig permits Swagger:
```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
```

---

## 8. Optional: Advanced Configuration

If you want more customization, you can create an OpenApiConfig class:

```java
// OPTIONAL: identity-service/src/main/java/com/payflow/identity/config/OpenApiConfig.java
package com.payflow.identity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("PayFlow Identity Service API")
                .version("1.0.0")
                .description("User authentication and authorization"));
    }
}
```

**This is optional** - the default auto-configuration works for most cases.

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    RELATED TOPICS                                            │
│                                                                              │
│  Client SDK Generation                                                      │
│  ────────────────────                                                       │
│  Use openapi-generator with /v3/api-docs to create TypeScript clients:     │
│  npx openapi-generator-cli generate -i http://localhost:8081/v3/api-docs   │
│    -g typescript-fetch -o ./generated-client                               │
│                                                                              │
│  API Documentation Hosting                                                  │
│  ─────────────────────────                                                  │
│  Export OpenAPI spec and host on SwaggerHub or ReadMe for public docs.     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ✅ Part 08 COMPLETE: Identity Swagger                                      │
│                                                                              │
│  NEXT: Part 09 - Identity Testing                                           │
│  ─────────────────────────────────                                          │
│  Add unit and integration tests for the Identity Service.                  │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  IDENTITY SERVICE BUILD PROGRESS                                    │   │
│  │                                                                      │   │
│  │  Part 04: Setup ✅                                                  │   │
│  │  Part 05: Database ✅      - Entity, migration, repository          │   │
│  │  Part 06: JWT Auth ✅      - JwtService, AuthService, Security      │   │
│  │  Part 07: Controllers ✅   - REST endpoints with ApiResponse        │   │
│  │  Part 08: Swagger ✅       - API documentation                      │   │
│  │  Part 09: Testing          - Unit and integration tests             │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Continue to: part-09-identity-testing.md                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 08 Complete!** 🎉

You now have:
- Swagger UI accessible at http://localhost:8081/swagger-ui.html
- OpenAPI spec available at http://localhost:8081/v3/api-docs
- Interactive API testing capability
- Understanding of SpringDoc auto-configuration
