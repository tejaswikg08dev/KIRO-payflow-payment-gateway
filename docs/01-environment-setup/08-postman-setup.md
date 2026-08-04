# Environment Setup — Postman Installation & Configuration

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## 1. What We're Building

In this guide, you'll install and configure **Postman** for API testing. Postman is essential for:
- Testing PayFlow REST APIs during development
- Exploring API requests and responses
- Creating automated test collections
- Sharing API documentation with team
- Debugging backend issues

---

## 2. Concepts Deep Dive

### What is Postman?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          API Testing with Postman                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   WITHOUT POSTMAN:                     WITH POSTMAN:                        │
│                                                                              │
│   Write code to test:                  Visual interface:                    │
│   ┌─────────────────────────┐          ┌─────────────────────────┐          │
│   │ fetch('/api/orders', {  │          │ POST /api/orders        │          │
│   │   method: 'POST',       │          │ Headers: Content-Type   │          │
│   │   headers: {...},       │          │ Body: { "amount": 100 } │          │
│   │   body: JSON.stringify( │          │                         │          │
│   │     { amount: 100 }     │          │ [SEND] button           │          │
│   │   )                     │          │                         │          │
│   │ });                     │          │ Response: 201 Created   │          │
│   └─────────────────────────┘          │ { "orderId": "ORD123" } │          │
│                                        └─────────────────────────┘          │
│   Problems:                            Benefits:                            │
│   • Slow iteration                     • Instant feedback                   │
│   • Hard to debug                      • See full request/response          │
│   • No history                         • Save and replay requests           │
│   • No collaboration                   • Share collections                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Postman Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Postman Concepts                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   WORKSPACE                                                                  │
│   └── Collection (PayFlow APIs)                                             │
│       ├── Folder: Authentication                                            │
│       │   ├── Request: Register User                                        │
│       │   ├── Request: Login                                                │
│       │   └── Request: Refresh Token                                        │
│       │                                                                      │
│       ├── Folder: Orders                                                    │
│       │   ├── Request: Create Order                                         │
│       │   ├── Request: Get Order                                            │
│       │   └── Request: List Orders                                          │
│       │                                                                      │
│       └── Folder: Payments                                                  │
│           ├── Request: Process Payment                                      │
│           ├── Request: Capture Payment                                      │
│           └── Request: Refund Payment                                       │
│                                                                              │
│   ENVIRONMENTS                                                               │
│   ├── Local:      base_url = http://localhost:8080                         │
│   ├── Dev:        base_url = https://dev-api.payflow.com                   │
│   └── Production: base_url = https://api.payflow.com                       │
│                                                                              │
│   VARIABLES                                                                  │
│   ├── {{base_url}}     → Environment-specific URL                          │
│   ├── {{api_key}}      → Your API key                                      │
│   └── {{order_id}}     → Dynamic value from previous request               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Why Postman for PayFlow?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PayFlow API Testing Workflow                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   DEVELOPMENT CYCLE:                                                         │
│                                                                              │
│   ┌─────────┐      ┌─────────┐      ┌─────────┐      ┌─────────┐           │
│   │  Write  │      │  Start  │      │  Test   │      │  Debug  │           │
│   │  Code   │ ──►  │  Server │ ──►  │  in     │ ──►  │  Fix    │ ──► ↺    │
│   │         │      │         │      │ Postman │      │  Issues │           │
│   └─────────┘      └─────────┘      └─────────┘      └─────────┘           │
│                                                                              │
│   POSTMAN CAPABILITIES FOR PAYFLOW:                                          │
│                                                                              │
│   1. Test Authentication Flow:                                              │
│      Register → Login → Get Token → Use in subsequent requests              │
│                                                                              │
│   2. Test Payment Flow:                                                     │
│      Create Order → Process Payment → Capture → Refund                      │
│                                                                              │
│   3. Automated Testing:                                                     │
│      Collection Runner → Run all tests → Check assertions                   │
│                                                                              │
│   4. CI/CD Integration:                                                     │
│      Newman (CLI) → Run in GitHub Actions → Fail on errors                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

| Requirement | Status |
|-------------|--------|
| Windows 10/11 | Required |
| Internet connection | Required |
| Free Postman account | Create at postman.com (optional but recommended) |

---

## 4. Step-by-Step Installation

### Step 4.1: Download Postman

1. Go to: https://www.postman.com/downloads/
2. Click **"Download the App"** (Windows 64-bit)
3. Save the installer

---

### Step 4.2: Install Postman

1. Run the downloaded **Postman-win64-x.x.x-Setup.exe**
2. Installation is automatic (no prompts)
3. Postman launches when complete

---

### Step 4.3: Create Account (Recommended)

1. Click **"Create Account"** or **"Sign In"**
2. Sign up with email or Google account
3. Verify email if required

**Why create an account?**
- Sync collections across devices
- Backup your work
- Share with team members
- Access Postman cloud features

*You can skip this and use Postman without an account.*

---

### Step 4.4: Initial Setup

After launching Postman:

1. **Skip the tour** (or take it if you're new)
2. You'll see the main interface:
   - Sidebar (Collections, APIs, Environments)
   - Request builder (center)
   - Response viewer (bottom)

---

## 5. Verification

### Create Your First Request

1. Click **"+"** button to create a new request tab
2. Set method to **GET**
3. Enter URL: `https://jsonplaceholder.typicode.com/posts/1`
4. Click **"Send"**

**Expected Response:**
```json
{
  "userId": 1,
  "id": 1,
  "title": "sunt aut facere repellat provident...",
  "body": "quia et suscipit..."
}
```

**Status:** `200 OK`  
**Time:** ~200-500ms

### Test POST Request

1. Create new request tab
2. Set method to **POST**
3. URL: `https://jsonplaceholder.typicode.com/posts`
4. Go to **Body** tab
5. Select **raw** and **JSON**
6. Enter:
```json
{
  "title": "Test Post",
  "body": "This is a test from PayFlow setup",
  "userId": 1
}
```
7. Click **"Send"**

**Expected Response:**
```json
{
  "id": 101,
  "title": "Test Post",
  "body": "This is a test from PayFlow setup",
  "userId": 1
}
```

**Status:** `201 Created`

---

## 6. Setting Up PayFlow Collection

### Step 6.1: Create Collection

1. Click **"Collections"** in sidebar
2. Click **"+"** to create new collection
3. Name it: **"PayFlow APIs"**
4. Add description: "API collection for PayFlow Payment Gateway"

### Step 6.2: Create Environment

1. Click the **Environment** dropdown (top right, shows "No Environment")
2. Click **"+"** to add new environment
3. Name: **"PayFlow Local"**
4. Add variables:

| Variable | Initial Value | Current Value |
|----------|---------------|---------------|
| `base_url` | `http://localhost:8080` | `http://localhost:8080` |
| `api_key` | (leave empty) | (leave empty) |
| `access_token` | (leave empty) | (leave empty) |
| `order_id` | (leave empty) | (leave empty) |
| `payment_id` | (leave empty) | (leave empty) |

5. Click **"Save"**
6. Select "PayFlow Local" from the dropdown

### Step 6.3: Create Folder Structure

In PayFlow APIs collection, create these folders:

1. Right-click collection → **"Add folder"**
2. Create folders:
   - **01 - Authentication**
   - **02 - Merchants**
   - **03 - Orders**
   - **04 - Payments**
   - **05 - Webhooks**
   - **06 - Settlements**

### Step 6.4: Add Sample Requests

**In "01 - Authentication" folder:**

**Request 1: Register User**
```
Method: POST
URL: {{base_url}}/v1/auth/register
Headers:
  Content-Type: application/json
Body (raw JSON):
{
  "email": "test@example.com",
  "password": "Test123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Request 2: Login**
```
Method: POST
URL: {{base_url}}/v1/auth/login
Headers:
  Content-Type: application/json
Body (raw JSON):
{
  "email": "test@example.com",
  "password": "Test123!"
}
```

Add a **Test script** to Login request (Tests tab):
```javascript
// Save token for subsequent requests
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("access_token", jsonData.accessToken);
    console.log("Token saved: " + jsonData.accessToken.substring(0, 20) + "...");
}
```

**In "03 - Orders" folder:**

**Request: Create Order**
```
Method: POST
URL: {{base_url}}/v1/orders
Headers:
  Content-Type: application/json
  Authorization: Bearer {{access_token}}
  X-Api-Key: {{api_key}}
Body (raw JSON):
{
  "amount": 10000,
  "currency": "USD",
  "description": "Test order",
  "metadata": {
    "orderId": "SHOP-123"
  }
}
```

Add Test script:
```javascript
if (pm.response.code === 201) {
    var jsonData = pm.response.json();
    pm.environment.set("order_id", jsonData.id);
    console.log("Order ID saved: " + jsonData.id);
}
```

---

## 7. Understanding Postman Features

### Request Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Postman Request Anatomy                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │  Method ▼  │  URL with Variables                                │       │
│   │  POST      │  {{base_url}}/v1/orders                            │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   ┌─ Tabs ──────────────────────────────────────────────────────────┐       │
│   │ Params │ Authorization │ Headers │ Body │ Pre-req │ Tests │ ... │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│   PARAMS:        Query parameters (?key=value)                              │
│   AUTHORIZATION: Auth type (Bearer, API Key, OAuth)                         │
│   HEADERS:       HTTP headers (Content-Type, etc.)                          │
│   BODY:          Request body (JSON, form-data, etc.)                       │
│   PRE-REQUEST:   JavaScript to run BEFORE request                           │
│   TESTS:         JavaScript to run AFTER response                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Variable Types

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Postman Variables Scope                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   SCOPE (highest to lowest priority):                                       │
│                                                                              │
│   ┌─────────────┐                                                           │
│   │   LOCAL     │ ◄── Temporary, within single request                     │
│   └─────────────┘                                                           │
│          │                                                                   │
│   ┌─────────────┐                                                           │
│   │   DATA      │ ◄── From CSV/JSON files in Collection Runner             │
│   └─────────────┘                                                           │
│          │                                                                   │
│   ┌─────────────┐                                                           │
│   │ ENVIRONMENT │ ◄── Most commonly used (base_url, tokens)                │
│   └─────────────┘                                                           │
│          │                                                                   │
│   ┌─────────────┐                                                           │
│   │ COLLECTION  │ ◄── Shared across collection (API version)               │
│   └─────────────┘                                                           │
│          │                                                                   │
│   ┌─────────────┐                                                           │
│   │   GLOBAL    │ ◄── Available everywhere (rarely used)                   │
│   └─────────────┘                                                           │
│                                                                              │
│   USAGE:  {{variable_name}}                                                 │
│                                                                              │
│   Example: {{base_url}}/v1/orders/{{order_id}}                             │
│            → http://localhost:8080/v1/orders/ORD-123                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Test Scripts (JavaScript)

```javascript
// Common test patterns for PayFlow:

// 1. Check status code
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

// 2. Check response time
pm.test("Response time < 500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(500);
});

// 3. Check JSON structure
pm.test("Response has required fields", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('id');
    pm.expect(jsonData).to.have.property('status');
});

// 4. Save value to environment
var jsonData = pm.response.json();
pm.environment.set("order_id", jsonData.id);

// 5. Chain requests
pm.test("Order ID is not null", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.id).to.not.be.null;
});

// 6. Check specific value
pm.test("Status is PENDING", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.status).to.eql("PENDING");
});
```

---

## 8. Key Takeaways

| Concept | Remember |
|---------|----------|
| **Collection** | Group of related requests |
| **Environment** | Variables for different servers (local, dev, prod) |
| **Variables** | Use `{{name}}` syntax, avoid hardcoding |
| **Tests** | JavaScript assertions that run after response |
| **Pre-request** | JavaScript that runs before sending request |
| **Collection Runner** | Run all requests in sequence |

---

## 9. Q&A / Troubleshooting

### "Could not send request"

**Fix:**
1. Check URL is correct
2. Check server is running
3. Check network/firewall
4. Try: `curl <url>` in terminal

### Variables not resolving

**Fix:**
1. Check environment is selected (top right dropdown)
2. Check variable name spelling
3. View resolved URL: hover over URL

### "401 Unauthorized"

**Fix:**
1. Check token is set in environment
2. Run Login request first
3. Check Authorization header format

### Tests not saving variables

**Fix:**
1. Check response code condition
2. Use console.log() to debug
3. View Postman Console: View → Show Postman Console

### SSL Certificate errors

**Fix:**
1. Settings → General → SSL certificate verification → OFF
2. Only for local development!

---

## 10. Next Steps

**Continue to:** [09-aws-account-setup.md](./09-aws-account-setup.md)

In the next guide, you'll set up your AWS account for cloud deployment.

**What you've accomplished:**
- ✅ Installed Postman
- ✅ Created first requests
- ✅ Set up PayFlow collection
- ✅ Configured environments
- ✅ Understand variables and tests
- ✅ Ready for API testing

---

**End of Postman Setup**
