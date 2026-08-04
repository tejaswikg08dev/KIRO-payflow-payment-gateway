# Sprint 2, Part 12: End-to-End Testing

**Duration:** 2-3 hours  
**Prerequisites:** Part 11 completed, All services running  
**Status:** 📘 CONCEPTUAL GUIDE (Future implementation)

> **Note:** This part provides a reference design for E2E testing of Sprint 2 features. The test code shown here serves as a guide for validating API key management functionality.

---

## 1. What We're Building

In this part, you'll create **end-to-end tests** to verify the API key management flow.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     E2E TEST FLOW (SPRINT 2)                                 │
│                                                                              │
│  Test: Complete API Key Lifecycle                                           │
│                                                                              │
│  1. Login (Prerequisite)                                                    │
│     POST /v1/auth/login                                                     │
│     └── Returns JWT token                                                   │
│          │                                                                   │
│          ▼                                                                   │
│  2. Generate TEST API Key                                                   │
│     POST /v1/merchants/{merchantId}/api-keys?keyType=TEST                   │
│     Headers: Authorization: Bearer {jwt}                                    │
│     ├── Returns public_key (pk_test_xxx)                                    │
│     └── Returns secret_key (sk_test_xxx)                                    │
│          │                                                                   │
│          ▼                                                                   │
│  3. Authenticate with API Key                                               │
│     GET /v1/merchants/{merchantId}                                          │
│     Headers: X-Api-Key: sk_test_xxx                                         │
│     ├── ApiKeyAuthFilter validates key                                      │
│     ├── Adds X-Merchant-Id header                                           │
│     └── Returns merchant data                                               │
│          │                                                                   │
│          ▼                                                                   │
│  4. List API Keys                                                           │
│     GET /v1/merchants/{merchantId}/api-keys                                 │
│     └── Returns all keys (secret masked)                                    │
│          │                                                                   │
│          ▼                                                                   │
│  5. Revoke API Key                                                          │
│     DELETE /v1/merchants/{merchantId}/api-keys/{keyId}                      │
│     └── Key status → REVOKED                                                │
│          │                                                                   │
│          ▼                                                                   │
│  6. Verify Revoked Key Fails                                                │
│     GET /v1/merchants/{merchantId}                                          │
│     Headers: X-Api-Key: sk_test_xxx (revoked)                               │
│     └── Returns 401 Unauthorized                                            │
│          │                                                                   │
│          ▼                                                                   │
│  ✅ All assertions pass                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 API Key Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    API KEY AUTHENTICATION                                    │
│                                                                              │
│  Client Request                                                             │
│       │                                                                      │
│       │ X-Api-Key: sk_test_abc123...                                        │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                    API GATEWAY                                    │       │
│  │                                                                   │       │
│  │  1. ApiKeyAuthFilter intercepts request                          │       │
│  │                                                                   │       │
│  │  2. Validate format (sk_test_* or sk_live_*)                     │       │
│  │                                                                   │       │
│  │  3. SHA-256 hash the key                                         │       │
│  │                                                                   │       │
│  │  4. Check Redis cache                                            │       │
│  │     ┌────────────┐                                               │       │
│  │     │ Cache HIT  │ → Parse merchantId:keyType:status             │       │
│  │     └────────────┘                                               │       │
│  │          or                                                       │       │
│  │     ┌────────────┐                                               │       │
│  │     │ Cache MISS │ → POST /internal/validate-api-key             │       │
│  │     └────────────┘   to merchant-service                         │       │
│  │                                                                   │       │
│  │  5. If valid → Add X-Merchant-Id header                          │       │
│  │     If invalid → Return 401                                      │       │
│  │                                                                   │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│       │                                                                      │
│       ▼                                                                      │
│  Downstream Service (merchant-service)                                      │
│  └── Receives X-Merchant-Id header for authorization                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Test Coverage Matrix

| Test Case | Method | Endpoint | Expected |
|-----------|--------|----------|----------|
| Generate TEST key | POST | /v1/merchants/{id}/api-keys?keyType=TEST | 201, pk_test_*, sk_test_* |
| Generate LIVE key | POST | /v1/merchants/{id}/api-keys?keyType=LIVE | 201, pk_live_*, sk_live_* |
| Auth with valid key | GET | /v1/merchants/{id} (X-Api-Key header) | 200 |
| Auth with invalid key | GET | /v1/merchants/{id} | 401 |
| List API keys | GET | /v1/merchants/{id}/api-keys | 200, array |
| Revoke key | DELETE | /v1/merchants/{id}/api-keys/{keyId} | 200 |
| Auth with revoked key | GET | /v1/merchants/{id} | 401 |
| Update webhook | PUT | /v1/merchants/{id}/webhook | 200 |

---

## 3. Step-by-Step Implementation

### Step 3.1: Create API Key E2E Test

**File: `e2e-tests/src/test/java/com/payflow/e2e/ApiKeyManagementE2ETest.java`**

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
class ApiKeyManagementE2ETest {

    private static String accessToken;
    private static String merchantId;
    private static String testKeyId;
    private static String testSecretKey;
    private static String testPublicKey;
    
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_EMAIL = "apikey-test-" + System.currentTimeMillis() + "@test.com";
    private static final String TEST_PASSWORD = "Test123!@#";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
        // Create user and merchant for testing
        setupTestMerchant();
    }

    private static void setupTestMerchant() {
        // Register user
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "%s",
                    "password": "%s",
                    "fullName": "API Key Test User",
                    "phone": "9876543210",
                    "role": "CUSTOMER"
                }
                """.formatted(TEST_EMAIL, TEST_PASSWORD))
        .when()
            .post("/v1/auth/register")
        .then()
            .statusCode(201)
            .extract().response();

        accessToken = registerResponse.jsonPath().getString("data.accessToken");

        // Create merchant
        Response merchantResponse = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body("""
                {
                    "businessName": "API Key Test Company",
                    "businessType": "COMPANY"
                }
                """)
        .when()
            .post("/v1/merchants")
        .then()
            .statusCode(201)
            .extract().response();

        merchantId = merchantResponse.jsonPath().getString("merchant.id");
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Generate TEST API key")
    void generateTestApiKey() {
        Response response = given()
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("keyType", "TEST")
        .when()
            .post("/v1/merchants/" + merchantId + "/api-keys")
        .then()
            .statusCode(201)
            .body("success", equalTo(true))
            .body("data.public_key", startsWith("pk_test_"))
            .body("data.secret_key", startsWith("sk_test_"))
            .body("data.key_type", equalTo("TEST"))
            .body("data.note", containsString("will NOT be shown again"))
            .extract().response();

        testKeyId = response.jsonPath().getString("data.key_id");
        testPublicKey = response.jsonPath().getString("data.public_key");
        testSecretKey = response.jsonPath().getString("data.secret_key");

        assertThat(testKeyId).startsWith("key_");
        assertThat(testSecretKey).hasSize(40); // sk_test_ (8) + 32 random chars
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Authenticate using API key")
    void authenticateWithApiKey() {
        given()
            .header("X-Api-Key", testSecretKey)
        .when()
            .get("/v1/merchants/" + merchantId)
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data.id", equalTo(merchantId))
            .body("data.businessName", equalTo("API Key Test Company"));
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Authentication fails with invalid key")
    void authenticationFailsWithInvalidKey() {
        given()
            .header("X-Api-Key", "sk_test_invalid_key_12345678901234")
        .when()
            .get("/v1/merchants/" + merchantId)
        .then()
            .statusCode(401)
            .body("error", containsString("Invalid API key"));
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Authentication fails with wrong key format")
    void authenticationFailsWithWrongFormat() {
        given()
            .header("X-Api-Key", "pk_test_public_key_should_fail")
        .when()
            .get("/v1/merchants/" + merchantId)
        .then()
            .statusCode(401)
            .body("error", containsString("Invalid API key format"));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: List API keys (secret masked)")
    void listApiKeys() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/merchants/" + merchantId + "/api-keys")
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data", hasSize(greaterThanOrEqualTo(1)))
            .body("data[0].keyId", notNullValue())
            .body("data[0].keyType", equalTo("TEST"))
            .body("data[0].status", equalTo("ACTIVE"))
            .body("data[0].keyPrefix", startsWith("sk_test_"))
            // Verify secret is NOT returned in list
            .body("data[0].secretKey", nullValue());
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Generate LIVE API key")
    void generateLiveApiKey() {
        given()
            .header("Authorization", "Bearer " + accessToken)
            .queryParam("keyType", "LIVE")
        .when()
            .post("/v1/merchants/" + merchantId + "/api-keys")
        .then()
            .statusCode(201)
            .body("data.public_key", startsWith("pk_live_"))
            .body("data.secret_key", startsWith("sk_live_"))
            .body("data.key_type", equalTo("LIVE"));
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Revoke TEST API key")
    void revokeApiKey() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .delete("/v1/merchants/" + merchantId + "/api-keys/" + testKeyId)
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data.message", containsString("revoked successfully"))
            .body("data.key_id", equalTo(testKeyId));
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Authentication fails with revoked key")
    void authenticationFailsWithRevokedKey() {
        // Small delay to allow cache invalidation
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        
        given()
            .header("X-Api-Key", testSecretKey)
        .when()
            .get("/v1/merchants/" + merchantId)
        .then()
            .statusCode(401)
            .body("error", containsString("revoked"));
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Verify key status is REVOKED in list")
    void verifyRevokedKeyInList() {
        Response response = given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/merchants/" + merchantId + "/api-keys")
        .then()
            .statusCode(200)
            .extract().response();

        // Find the revoked key
        var keys = response.jsonPath().getList("data");
        var revokedKey = keys.stream()
            .filter(k -> ((java.util.Map<?, ?>) k).get("keyId").equals(testKeyId))
            .findFirst()
            .orElseThrow();
        
        assertThat(((java.util.Map<?, ?>) revokedKey).get("status")).isEqualTo("REVOKED");
    }
}
```

### Step 3.2: Create Webhook E2E Test

**File: `e2e-tests/src/test/java/com/payflow/e2e/WebhookConfigE2ETest.java`**

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
class WebhookConfigE2ETest {

    private static String accessToken;
    private static String merchantId;
    private static String webhookSecret;
    
    private static final String BASE_URL = "http://localhost:8080";
    private static final String TEST_EMAIL = "webhook-test-" + System.currentTimeMillis() + "@test.com";

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = BASE_URL;
        setupTestMerchant();
    }

    private static void setupTestMerchant() {
        // Register and create merchant (similar to ApiKeyManagementE2ETest)
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "%s",
                    "password": "Test123!@#",
                    "fullName": "Webhook Test User",
                    "phone": "9876543210",
                    "role": "CUSTOMER"
                }
                """.formatted(TEST_EMAIL))
        .when()
            .post("/v1/auth/register")
        .then()
            .statusCode(201)
            .extract().response();

        accessToken = registerResponse.jsonPath().getString("data.accessToken");

        Response merchantResponse = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body("""
                {
                    "businessName": "Webhook Test Company",
                    "businessType": "COMPANY"
                }
                """)
        .when()
            .post("/v1/merchants")
        .then()
            .statusCode(201)
            .extract().response();

        merchantId = merchantResponse.jsonPath().getString("merchant.id");
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Get initial webhook config (empty)")
    void getInitialWebhookConfig() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/merchants/" + merchantId + "/webhook")
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data.webhookUrl", nullValue())
            .body("data.webhookSecret", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Update webhook URL")
    void updateWebhookUrl() {
        String webhookUrl = "https://api.testcompany.com/webhooks/payflow";
        
        Response response = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body("""
                {
                    "webhookUrl": "%s"
                }
                """.formatted(webhookUrl))
        .when()
            .put("/v1/merchants/" + merchantId + "/webhook")
        .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("data.webhookUrl", equalTo(webhookUrl))
            .body("data.webhookSecret", notNullValue())
            .extract().response();

        webhookSecret = response.jsonPath().getString("data.webhookSecret");
        assertThat(webhookSecret).startsWith("whsec_");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Verify webhook URL persisted")
    void verifyWebhookUrlPersisted() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/v1/merchants/" + merchantId + "/webhook")
        .then()
            .statusCode(200)
            .body("data.webhookUrl", equalTo("https://api.testcompany.com/webhooks/payflow"))
            .body("data.webhookSecret", equalTo(webhookSecret));
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Update webhook URL regenerates secret")
    void updateWebhookRegeneratesSecret() {
        String newWebhookUrl = "https://api.testcompany.com/v2/webhooks/payflow";
        
        Response response = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body("""
                {
                    "webhookUrl": "%s"
                }
                """.formatted(newWebhookUrl))
        .when()
            .put("/v1/merchants/" + merchantId + "/webhook")
        .then()
            .statusCode(200)
            .body("data.webhookUrl", equalTo(newWebhookUrl))
            .extract().response();

        String newSecret = response.jsonPath().getString("data.webhookSecret");
        // New secret should be different from old one
        assertThat(newSecret).isNotEqualTo(webhookSecret);
        assertThat(newSecret).startsWith("whsec_");
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Invalid webhook URL rejected")
    void invalidWebhookUrlRejected() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + accessToken)
            .body("""
                {
                    "webhookUrl": "not-a-valid-url"
                }
                """)
        .when()
            .put("/v1/merchants/" + merchantId + "/webhook")
        .then()
            .statusCode(400)
            .body("success", equalTo(false));
    }
}
```

### Step 3.3: Create Frontend E2E Test (Playwright)

**File: `frontend-dashboard/e2e/api-keys.spec.ts`**

```typescript
import { test, expect } from '@playwright/test';

test.describe('API Keys Page', () => {
  test.beforeEach(async ({ page }) => {
    // Login first
    await page.goto('/login');
    await page.fill('input[type="email"]', 'test@example.com');
    await page.fill('input[type="password"]', 'Test123!@#');
    await page.click('button[type="submit"]');
    
    // Wait for dashboard and navigate to API keys
    await page.waitForURL('/dashboard');
    await page.click('text=Manage API Keys');
    await page.waitForURL('/api-keys');
  });

  test('should display API Keys page', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('API Keys & Webhooks');
    await expect(page.locator('text=Generate TEST Key')).toBeVisible();
    await expect(page.locator('text=Generate LIVE Key')).toBeVisible();
  });

  test('should generate TEST API key', async ({ page }) => {
    await page.click('text=Generate TEST Key');
    
    // Wait for new key modal
    await expect(page.locator('text=New API Key Generated')).toBeVisible();
    await expect(page.locator('text=pk_test_')).toBeVisible();
    await expect(page.locator('text=sk_test_')).toBeVisible();
    
    // Dismiss modal
    await page.click("text=I've saved my secret key");
    await expect(page.locator('text=New API Key Generated')).not.toBeVisible();
  });

  test('should list API keys in table', async ({ page }) => {
    // Generate a key first
    await page.click('text=Generate TEST Key');
    await page.click("text=I've saved my secret key");
    
    // Verify table shows the key
    await expect(page.locator('table')).toBeVisible();
    await expect(page.locator('td:has-text("TEST")')).toBeVisible();
    await expect(page.locator('td:has-text("ACTIVE")')).toBeVisible();
    await expect(page.locator('td:has-text("sk_test_")')).toBeVisible();
  });

  test('should revoke API key', async ({ page }) => {
    // Generate a key first
    await page.click('text=Generate TEST Key');
    await page.click("text=I've saved my secret key");
    
    // Click revoke button
    page.on('dialog', dialog => dialog.accept()); // Handle confirm dialog
    await page.click('button:has-text("Revoke")');
    
    // Verify status changed
    await expect(page.locator('td:has-text("REVOKED")')).toBeVisible();
  });

  test('should copy keys to clipboard', async ({ page }) => {
    await page.click('text=Generate TEST Key');
    
    // Click copy button for public key
    const copyButtons = page.locator('button:has-text("Copy")');
    await copyButtons.first().click();
    
    // Verify alert shows
    page.on('dialog', async dialog => {
      expect(dialog.message()).toContain('Copied');
      await dialog.accept();
    });
  });

  test('should update webhook URL', async ({ page }) => {
    const webhookUrl = 'https://test.example.com/webhooks';
    
    await page.fill('input[placeholder*="webhook"]', webhookUrl);
    await page.click('button:has-text("Save")');
    
    // Verify alert
    page.on('dialog', async dialog => {
      expect(dialog.message()).toContain('Webhook URL updated');
      await dialog.accept();
    });
  });

  test('should show/hide webhook secret', async ({ page }) => {
    // Initially hidden
    await expect(page.locator('text=••••••••••••••••')).toBeVisible();
    
    // Click show
    await page.click('button:has-text("Show")');
    await expect(page.locator('text=whsec_')).toBeVisible();
    
    // Click hide
    await page.click('button:has-text("Hide")');
    await expect(page.locator('text=••••••••••••••••')).toBeVisible();
  });
});
```

---

## 4. Running E2E Tests

### 4.1 Prerequisites

Ensure all services are running:

```powershell
# Start infrastructure
docker compose -f docker-compose-infra.yml up -d

# Start backend services (in separate terminals)
cd service-registry && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
cd identity-service && mvn spring-boot:run
cd merchant-service && mvn spring-boot:run

# Start frontend
cd frontend-dashboard && npm run dev
```

### 4.2 Run Backend E2E Tests

```powershell
cd e2e-tests
mvn test -Dtest=ApiKeyManagementE2ETest
mvn test -Dtest=WebhookConfigE2ETest
```

### 4.3 Run Frontend E2E Tests

```powershell
cd frontend-dashboard
npx playwright test e2e/api-keys.spec.ts
```

---

## 5. Test Verification Checklist

### API Key Generation
- [ ] POST creates key with correct prefix (pk_test_, sk_test_)
- [ ] Secret key is 40 characters (prefix + 32 random)
- [ ] Key ID starts with `key_`
- [ ] Response includes warning about secret not shown again

### API Key Authentication
- [ ] Valid sk_test_* key authenticates successfully
- [ ] Valid sk_live_* key authenticates successfully
- [ ] pk_* keys are rejected (format error)
- [ ] Invalid keys return 401
- [ ] X-Merchant-Id header added to downstream requests

### Key Management
- [ ] List returns all keys (secrets masked)
- [ ] Revoke changes status to REVOKED
- [ ] Revoked keys fail authentication
- [ ] Cache invalidation works (within 5 min TTL)

### Webhook Configuration
- [ ] GET returns current config
- [ ] PUT updates webhook URL
- [ ] PUT regenerates webhook secret
- [ ] Invalid URLs rejected
- [ ] Secret starts with `whsec_`

### Frontend
- [ ] ApiKeysPage loads
- [ ] Generate buttons work
- [ ] New key modal shows public + secret
- [ ] Keys table displays correctly
- [ ] Revoke button works with confirmation
- [ ] Copy to clipboard works
- [ ] Webhook URL can be updated
- [ ] Webhook secret show/hide works

---

## 6. Key Takeaways

| Concept | Remember |
|---------|----------|
| **API Key E2E** | Test full lifecycle: generate → use → revoke |
| **Auth Testing** | Test both valid and invalid scenarios |
| **Cache Testing** | Allow time for cache invalidation |
| **Frontend E2E** | Use Playwright for React component testing |
| **Test Order** | Use @Order for dependent tests |

---

## 7. Next Steps

**Continue to:** [part-13-git-pr.md](./part-13-git-pr.md)

In the next part, you'll commit Sprint 2 changes and create a pull request.

---

**End of Sprint 2, Part 12**
