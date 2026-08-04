# Sprint 1, Part 22: End-to-End Testing

**Duration:** 2-3 hours  
**Prerequisites:** Part 21 completed, All services running  
**Status:** 📘 CONCEPTUAL GUIDE (Future implementation)

> **Note:** This part provides a reference design for E2E testing. The `e2e-tests/` module shown here is **NOT yet implemented** in the codebase. This serves as a guide for future E2E test implementation.

---

## 1. What We're Building

In this part, you'll create **end-to-end tests** to verify the complete user flow.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     E2E TEST FLOW                                            │
│                                                                              │
│  Test: Complete User Registration & Merchant Onboarding                     │
│                                                                              │
│  1. Register User                                                           │
│     POST /v1/auth/register                                                  │
│     ├── Creates user in Identity Service                                    │
│     └── Returns JWT tokens                                                  │
│          │                                                                   │
│          ▼                                                                   │
│  2. Login User                                                              │
│     POST /v1/auth/login                                                     │
│     ├── Verifies credentials                                                │
│     └── Returns fresh tokens                                                │
│          │                                                                   │
│          ▼                                                                   │
│  3. Create Merchant                                                         │
│     POST /v1/merchants                                                      │
│     Headers: Authorization: Bearer {token}                                  │
│     ├── Creates merchant profile                                            │
│     └── Generates API keys (TEST + LIVE)                                    │
│          │                                                                   │
│          ▼                                                                   │
│  4. Verify Merchant                                                         │
│     GET /v1/merchants/me                                                    │
│     └── Returns created merchant data                                       │
│          │                                                                   │
│          ▼                                                                   │
│  ✅ All assertions pass                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Testing Pyramid

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TESTING PYRAMID                                           │
│                                                                              │
│                         /\                                                  │
│                        /  \                                                 │
│                       / E2E \     ← Few, slow, expensive                    │
│                      /────────\      Tests full user flows                  │
│                     /          \                                            │
│                    / Integration \  ← Some, medium speed                    │
│                   /──────────────\    Tests component interactions          │
│                  /                \                                         │
│                 /    Unit Tests    \ ← Many, fast, cheap                    │
│                /────────────────────\  Tests individual functions           │
│                                                                              │
│  E2E Tests in PayFlow:                                                      │
│  ────────────────────                                                       │
│  • Use REST Assured (Java)                                                  │
│  • Run against live services                                                │
│  • Test critical user journeys                                              │
│  • Run in CI/CD pipeline                                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation

### Step 3.1: Create E2E Test Module

**File: `e2e-tests/pom.xml`**

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

    <artifactId>e2e-tests</artifactId>
    <name>PayFlow E2E Tests</name>

    <dependencies>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <version>5.3.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.24.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```


### Step 3.2: Create E2E Test

**File: `e2e-tests/src/test/java/com/payflow/e2e/UserOnboardingE2ETest.java`**

```java
package com.payflow.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserOnboardingE2ETest {

    private static String accessToken;
    private static String userId;
    private static String merchantId;
    
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_EMAIL = "e2e-test-" + System.currentTimeMillis() + "@test.com";
    private static final String TEST_PASSWORD = "Test123!@#";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Register new user")
    void registerUser() {
        Response response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "%s",
                    "password": "%s",
                    "fullName": "E2E Test User",
                    "phone": "9876543210",
                    "role": "CUSTOMER"
                }
                """.formatted(TEST_EMAIL, TEST_PASSWORD))
        .when()
            .post("/v1/auth/register")
        .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("data.accessToken", notNullValue())
            .body("data.user.email", equalTo(TEST_EMAIL))
            .extract().response();

        accessToken = response.jsonPath().getString("data.accessToken");
        userId = response.jsonPath().getString("data.user.id");
        
        assertThat(accessToken).isNotEmpty();
        assertThat(userId).isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Login with registered user")
    void loginUser() {
        Response response = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(TEST_EMAIL, TEST_PASSWORD))
        .when()
            .post("/v1/auth/login")
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data.accessToken", notNullValue())
            .extract().response();

        accessToken = response.jsonPath().getString("data.accessToken");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Create merchant account")
    void createMerchant() {
        Response response = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body("""
                {
                    "businessName": "E2E Test Company",
                    "businessType": "COMPANY"
                }
                """)
        .when()
            .post("/v1/merchants")
        .then()
            .statusCode(201)
            .body("merchant.businessName", equalTo("E2E Test Company"))
            .body("testKeys.publicKey", startsWith("pk_test_"))
            .body("testKeys.secretKey", startsWith("sk_test_"))
            .body("liveKeys.publicKey", startsWith("pk_live_"))
            .extract().response();

        merchantId = response.jsonPath().getString("merchant.id");
        assertThat(merchantId).isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Get merchant profile")
    void getMerchant() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/merchants/me")
        .then()
            .statusCode(200)
            .body("id", equalTo(merchantId))
            .body("businessName", equalTo("E2E Test Company"))
            .body("status", equalTo("PENDING"));
    }
}
```

---

## 4. Verification

```powershell
# Run E2E tests
cd e2e-tests
mvn test
```

---

## 5. Key Takeaways

| Concept | Remember |
|---------|----------|
| **E2E Tests** | Test full user journeys |
| **REST Assured** | Java HTTP testing library |
| **Test Order** | @Order annotation |
| **Shared state** | Static variables between tests |

---

## 6. Next Steps

**Continue to:** [part-23-git-pr.md](./part-23-git-pr.md)

---

**End of Sprint 1, Part 22**
