# Sprint 1, Part 13: Merchant Swagger Testing

**Duration:** 1-2 hours  
**Prerequisites:** Part 12 completed, Merchant Service running

---

## 1. What We're Building

In this part, you'll explore the **Swagger UI** documentation for the Merchant Service and test the API endpoints interactively.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     THIS PART COVERS                                         │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    SWAGGER/OPENAPI                                   │   │
│  │                                                                      │   │
│  │  • Interactive API documentation                                    │   │
│  │  • Try endpoints directly in browser                                │   │
│  │  • Auto-generated from annotations                                  │   │
│  │                                                                      │   │
│  │  Access: http://localhost:8082/swagger-ui.html                      │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    API ENDPOINTS TO TEST                             │   │
│  │                                                                      │   │
│  │  POST  /v1/merchants                   Create new merchant          │   │
│  │  GET   /v1/merchants/{merchantId}      Get merchant by ID           │   │
│  │  POST  /v1/merchants/{merchantId}/api-keys  Generate API keys       │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 OpenAPI/Swagger

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OPENAPI SPECIFICATION                                     │
│                                                                              │
│  What is OpenAPI?                                                           │
│  ─────────────────                                                          │
│  • Standard for describing REST APIs                                        │
│  • Language-agnostic (JSON/YAML format)                                     │
│  • Machine-readable (for code generation)                                   │
│  • Human-readable (for documentation)                                       │
│                                                                              │
│  What is Swagger?                                                           │
│  ─────────────────                                                          │
│  • Toolset for working with OpenAPI                                         │
│  • Swagger UI: Interactive documentation                                    │
│  • Swagger Editor: API design tool                                          │
│  • Swagger Codegen: Generate client SDKs                                    │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                    SPRINGDOC FLOW                                  │    │
│  │                                                                     │    │
│  │  Java Code                    OpenAPI Spec                         │    │
│  │  ──────────                   ────────────                         │    │
│  │  @RestController    ──►       paths:                               │    │
│  │  @GetMapping          ──►       /v1/merchants:                     │    │
│  │  @Operation           ──►         get:                             │    │
│  │  @Parameter           ──►           parameters:                    │    │
│  │  @ApiResponse         ──►           responses:                     │    │
│  │                                                                     │    │
│  │                       Generated automatically by SpringDoc         │    │
│  │                                                                     │    │
│  │  OpenAPI Spec         ──►     Swagger UI                           │    │
│  │  (JSON/YAML)                  (HTML interface)                     │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 SpringDoc OpenAPI Integration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SPRINGDOC CONFIGURATION                                   │
│                                                                              │
│  PayFlow uses SpringDoc to generate OpenAPI documentation.                  │
│                                                                              │
│  Configuration in application.yml:                                          │
│  ─────────────────────────────────                                          │
│  springdoc:                                                                 │
│    swagger-ui:                                                              │
│      path: /swagger-ui.html                                                 │
│                                                                              │
│  Configuration in MerchantServiceApplication.java:                          │
│  ──────────────────────────────────────────────────                         │
│  @OpenAPIDefinition(info = @Info(                                           │
│      title = "PayFlow Merchant Service API",                                │
│      version = "1.0",                                                       │
│      description = "Merchant onboarding, API key management..."             │
│  ))                                                                         │
│                                                                              │
│  Annotations Used in Controller:                                            │
│  ──────────────────────────────                                             │
│  @Tag(name = "Merchants")         → Groups endpoints                       │
│  @Operation(summary = "...")      → Describes endpoint                     │
│                                                                              │
│  URLs:                                                                      │
│  ─────                                                                      │
│  • Swagger UI: http://localhost:8082/swagger-ui.html                       │
│  • OpenAPI JSON: http://localhost:8082/v3/api-docs                         │
│  • OpenAPI YAML: http://localhost:8082/v3/api-docs.yaml                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Ensure infrastructure is running
docker-compose -f docker-compose-infra.yml up -d

# Start Merchant Service
cd merchant-service
mvn spring-boot:run
```

Verify service is running:
```powershell
curl http://localhost:8082/actuator/health
# Expected: {"status":"UP"}
```

---

## 4. Step-by-Step Testing

### Step 4.1: Access Swagger UI

Open your browser and navigate to:
```
http://localhost:8082/swagger-ui.html
```

You should see:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SWAGGER UI INTERFACE                                      │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │                                                                     │    │
│  │  PayFlow Merchant Service API                                      │    │
│  │  Version: 1.0                                                      │    │
│  │                                                                     │    │
│  │  Merchant onboarding, API key management, and fee configuration    │    │
│  │                                                                     │    │
│  │  ─────────────────────────────────────────────────────────────     │    │
│  │                                                                     │    │
│  │  Merchants      (Merchant onboarding and management)               │    │
│  │                                                                     │    │
│  │  ▼ POST   /v1/merchants               Register a new merchant      │    │
│  │  ▼ GET    /v1/merchants/{merchantId}  Get merchant by ID           │    │
│  │  ▼ POST   /v1/merchants/{merchantId}/api-keys  Generate API keys   │    │
│  │                                                                     │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.2: Test Create Merchant Endpoint

1. Click on **POST /v1/merchants** to expand
2. Click **Try it out** button
3. Enter the following request body:

```json
{
  "userId": "usr_test12345",
  "businessName": "Acme Electronics",
  "businessType": "RETAIL"
}
```

4. Click **Execute**

**Expected Response (201 Created):**

```json
{
  "success": true,
  "data": {
    "id": "mch_XyZ1234567",
    "userId": "usr_test12345",
    "businessName": "Acme Electronics",
    "businessType": "RETAIL",
    "settlementSchedule": "T+2",
    "mdrPercentage": 2.00,
    "status": "PENDING",
    "kycVerified": false,
    "webhookSecret": "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
    "createdAt": "2026-08-04T10:30:00Z",
    "updatedAt": "2026-08-04T10:30:00Z"
  }
}
```

**⚠️ Important: Copy the `id` value (e.g., `mch_XyZ1234567`) for the next tests!**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    UNDERSTANDING THE RESPONSE                                │
│                                                                              │
│  Field                   Description                                        │
│  ─────                   ───────────                                        │
│  id                      10-char ID with "mch_" prefix                     │
│  userId                  Links to Identity Service user                    │
│  businessName            Merchant's business name                          │
│  businessType            RETAIL, ECOMMERCE, etc.                           │
│  settlementSchedule      When funds settle (T+2 = 2 days)                  │
│  mdrPercentage           Merchant Discount Rate (fee %)                    │
│  status                  PENDING → ACTIVE (after KYC)                      │
│  kycVerified             KYC document verification status                  │
│  webhookSecret           32-char secret for HMAC webhook signing           │
│  createdAt/updatedAt     Instant timestamps                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.3: Test Get Merchant Endpoint

1. Click on **GET /v1/merchants/{merchantId}** to expand
2. Click **Try it out** button
3. Enter the merchant ID from the previous step: `mch_XyZ1234567`
4. Click **Execute**

**Expected Response (200 OK):**

```json
{
  "success": true,
  "data": {
    "id": "mch_XyZ1234567",
    "userId": "usr_test12345",
    "businessName": "Acme Electronics",
    "businessType": "RETAIL",
    ...
  }
}
```

**Test Error Case - Non-existent Merchant:**

Try with a fake ID: `mch_DOESNOTEX`

**Expected Response (404 Not Found):**

```json
{
  "success": false,
  "message": "Merchant not found with id: mch_DOESNOTEX"
}
```

### Step 4.4: Test Generate API Keys Endpoint

1. Click on **POST /v1/merchants/{merchantId}/api-keys** to expand
2. Click **Try it out** button
3. Enter:
   - **merchantId:** `mch_XyZ1234567` (your merchant ID)
   - **keyType:** `TEST` (or `LIVE`)
4. Click **Execute**

**Expected Response (201 Created):**

```json
{
  "success": true,
  "data": {
    "key_id": "key_AbCdEfGhIj",
    "key_type": "TEST",
    "public_key": "pk_test_abc123def456ghi789",
    "secret_key": "sk_test_EXAMPLE_DO_NOT_USE...",
    "note": "Save the secret_key now. It will NOT be shown again."
  }
}
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ⚠️  CRITICAL WARNING                                      │
│                                                                              │
│  The secret_key is shown ONLY THIS ONE TIME!                                │
│                                                                              │
│  After this response:                                                       │
│  • secret_key is hashed (SHA-256) and stored                               │
│  • Original secret_key is discarded                                        │
│  • Cannot be retrieved or recovered                                        │
│                                                                              │
│  If you lose the secret_key:                                                │
│  • Generate a new API key pair                                             │
│  • Revoke the old key (not implemented yet)                                │
│                                                                              │
│  In Production:                                                             │
│  • Copy immediately to secure location                                     │
│  • Store in environment variables or secrets manager                       │
│  • Never commit to source code                                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Step 4.5: Generate LIVE API Keys

Repeat Step 4.4 with `keyType=LIVE`:

```
POST /v1/merchants/mch_XyZ1234567/api-keys?keyType=LIVE
```

**Response:**

```json
{
  "success": true,
  "data": {
    "key_id": "key_JkLmNoPqRs",
    "key_type": "LIVE",
    "public_key": "pk_live_abc123def456ghi789",
    "secret_key": "sk_live_EXAMPLE_DO_NOT_USE...",
    "note": "Save the secret_key now. It will NOT be shown again."
  }
}
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TEST vs LIVE KEY DIFFERENCES                              │
│                                                                              │
│  TEST Keys (pk_test_*, sk_test_*):                                          │
│  ─────────────────────────────────                                          │
│  • Use for development and testing                                         │
│  • Process test payments only                                              │
│  • No real money involved                                                  │
│  • Safe to expose in test environments                                     │
│                                                                              │
│  LIVE Keys (pk_live_*, sk_live_*):                                          │
│  ─────────────────────────────────                                          │
│  • Use for production only                                                 │
│  • Process real payments                                                   │
│  • Real money transfers                                                    │
│  • Protect with extreme care                                               │
│                                                                              │
│  The prefix makes it easy to identify key type:                            │
│  • pk_test_... → Safe for frontend, test mode                             │
│  • sk_test_... → Backend only, test mode                                  │
│  • pk_live_... → Safe for frontend, production                            │
│  • sk_live_... → Backend only, production (most sensitive!)               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### 5.1 Verify Data in Database

```powershell
# Check merchants table
docker exec -it payflow-postgres psql -U payflow -d payflow -c "SELECT id, business_name, status FROM merchant.merchants;"

# Check api_keys table
docker exec -it payflow-postgres psql -U payflow -d payflow -c "SELECT id, merchant_id, key_type, key_prefix, status FROM merchant.api_keys;"
```

**Expected Output:**

```
 id            | business_name     | status
---------------+-------------------+---------
 mch_XyZ123456 | Acme Electronics  | PENDING


 id            | merchant_id    | key_type | key_prefix    | status
---------------+----------------+----------+---------------+--------
 key_AbCdEfGhIj| mch_XyZ123456  | TEST     | sk_test_abc1  | ACTIVE
 key_JkLmNoPqRs| mch_XyZ123456  | LIVE     | sk_live_xyz7  | ACTIVE
```

### 5.2 Check OpenAPI JSON

```powershell
curl http://localhost:8082/v3/api-docs
```

This returns the full OpenAPI specification in JSON format.

---

## 6. File Structure

The Merchant Service structure after this part:

```
merchant-service/
├── pom.xml
└── src/main/
    ├── java/com/payflow/merchant/
    │   ├── MerchantServiceApplication.java  ← Has @OpenAPIDefinition
    │   ├── controller/
    │   │   └── MerchantController.java      ← Has @Tag, @Operation
    │   ├── model/
    │   │   ├── Merchant.java
    │   │   └── ApiKey.java
    │   ├── repository/
    │   │   ├── MerchantRepository.java
    │   │   └── ApiKeyRepository.java
    │   └── service/
    │       └── MerchantService.java
    └── resources/
        ├── application.yml                   ← Has springdoc.swagger-ui.path
        └── db/migration/
            └── V1__create_merchant_schema.sql
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KEY LEARNINGS                                        │
│                                                                              │
│  1. SpringDoc Integration                                                   │
│  ────────────────────────                                                   │
│  • Minimal configuration needed                                            │
│  • @OpenAPIDefinition on main class                                        │
│  • @Tag and @Operation on controller                                       │
│  • Auto-generates Swagger UI                                               │
│                                                                              │
│  2. API Response Patterns                                                   │
│  ────────────────────────                                                   │
│  • All responses wrapped in ApiResponse                                    │
│  • success: true/false indicates status                                    │
│  • data: contains payload                                                  │
│  • message: contains error details                                         │
│                                                                              │
│  3. API Key Security                                                        │
│  ───────────────────                                                        │
│  • Secret shown once only                                                  │
│  • SHA-256 hash stored in database                                         │
│  • Key prefix helps identification                                         │
│  • TEST vs LIVE separation                                                 │
│                                                                              │
│  4. Error Handling                                                          │
│  ─────────────────                                                          │
│  • ResourceNotFoundException → 404                                         │
│  • Handled by GlobalExceptionHandler in common-lib                         │
│  • Consistent error response format                                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Common Issues and Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Swagger UI not loading | Service not running | Start with `mvn spring-boot:run` |
| 404 on `/swagger-ui.html` | Wrong path | Check springdoc config in application.yml |
| "Merchant not found" | Invalid merchant ID | Use ID from create response |
| IllegalArgumentException | Invalid keyType | Use "TEST" or "LIVE" exactly |
| Empty response body | Database not running | Start Docker Postgres |

### Debug Checklist

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TROUBLESHOOTING CHECKLIST                                 │
│                                                                              │
│  □ Merchant Service started on port 8082?                                   │
│    curl http://localhost:8082/actuator/health                              │
│                                                                              │
│  □ PostgreSQL container running?                                            │
│    docker ps | grep postgres                                                │
│                                                                              │
│  □ merchant schema exists?                                                  │
│    docker exec -it payflow-postgres psql -U payflow -d payflow -c "\dn"            │
│                                                                              │
│  □ SpringDoc dependency in pom.xml?                                         │
│    Check for springdoc-openapi-starter-webmvc-ui                           │
│                                                                              │
│  □ OpenAPI accessible?                                                      │
│    curl http://localhost:8082/v3/api-docs                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Related Concepts

### OpenAPI Annotations Reference

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OPENAPI ANNOTATION CHEAT SHEET                            │
│                                                                              │
│  Class Level:                                                               │
│  ────────────                                                               │
│  @OpenAPIDefinition     → API metadata (title, version, description)       │
│  @Tag                   → Groups endpoints (used on controllers)           │
│                                                                              │
│  Method Level:                                                              │
│  ─────────────                                                              │
│  @Operation             → Describes an endpoint                            │
│    summary: short description                                              │
│    description: detailed description (supports markdown)                   │
│                                                                              │
│  Parameter Level:                                                           │
│  ────────────────                                                           │
│  @Parameter             → Describes path/query parameter                   │
│    description: what it is                                                 │
│    example: sample value                                                   │
│                                                                              │
│  Response Level:                                                            │
│  ───────────────                                                            │
│  @ApiResponses          → Documents possible responses                     │
│  @ApiResponse           → Single response code                             │
│    responseCode: HTTP status                                               │
│    description: when this happens                                          │
│                                                                              │
│  Schema Level:                                                              │
│  ─────────────                                                              │
│  @Schema                → Describes model fields                           │
│    description: field purpose                                              │
│    example: sample value                                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### API Response Wrapper

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ApiResponse FROM COMMON-LIB                               │
│                                                                              │
│  public class ApiResponse<T> {                                              │
│      private boolean success;                                               │
│      private T data;                                                        │
│      private String message;                                                │
│                                                                              │
│      public static <T> ApiResponse<T> success(T data) {                    │
│          return new ApiResponse<>(true, data, null);                       │
│      }                                                                      │
│                                                                              │
│      public static <T> ApiResponse<T> error(String message) {              │
│          return new ApiResponse<>(false, null, message);                   │
│      }                                                                      │
│  }                                                                          │
│                                                                              │
│  Usage in Controller:                                                       │
│  ────────────────────                                                       │
│  return ResponseEntity.ok(ApiResponse.success(merchant));                  │
│  return ResponseEntity.status(201).body(ApiResponse.success(created));     │
│                                                                              │
│  Success Response:        Error Response:                                   │
│  {                        {                                                 │
│    "success": true,         "success": false,                              │
│    "data": {...}            "message": "Not found"                         │
│  }                        }                                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

In the next part, we'll set up the **Frontend Dashboard** (React):

1. Create React application using Vite
2. Set up routing with React Router
3. Configure Tailwind CSS for styling
4. Create base layout components

**Navigation:**
- [Previous: Part 12 - Merchant Registration](./part-12-merchant-registration.md)
- [Next: Part 14 - Frontend Dashboard Setup](./part-14-frontend-dashboard-setup.md)

---

## Quick Reference

### Swagger URLs

| URL | Description |
|-----|-------------|
| `http://localhost:8082/swagger-ui.html` | Interactive UI |
| `http://localhost:8082/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8082/v3/api-docs.yaml` | OpenAPI YAML |

### Test Endpoints Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/v1/merchants` | Create merchant |
| GET | `/v1/merchants/{id}` | Get merchant |
| POST | `/v1/merchants/{id}/api-keys?keyType=TEST` | Generate TEST keys |
| POST | `/v1/merchants/{id}/api-keys?keyType=LIVE` | Generate LIVE keys |
