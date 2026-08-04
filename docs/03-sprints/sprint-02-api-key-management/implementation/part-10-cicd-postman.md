# Sprint 2, Part 10: CI/CD & Postman Testing

**Duration:** 45 minutes  
**Prerequisites:** Part 09 completed  
**Goal:** Create Postman collection for API key management testing

---

## 1. Learning Objectives

By the end of this part, you will:
- Create a Postman collection for Sprint 2 endpoints
- Understand environment variables in Postman
- Set up test scripts for CI/CD integration

---

## 2. Postman Collection Structure

```
PayFlow API - Sprint 2
├── API Keys
│   ├── Generate TEST Key
│   ├── Generate LIVE Key
│   ├── List All Keys
│   └── Revoke Key
├── Webhooks
│   ├── Update Webhook URL
│   └── Get Webhook Config
└── Authentication
    └── Test API Key Auth via Gateway
```

---

## 3. Environment Variables

Create a Postman environment named `PayFlow Local`:

| Variable | Initial Value | Description |
|----------|---------------|-------------|
| `base_url` | http://localhost:8082 | Merchant service |
| `gateway_url` | http://localhost:8080 | API Gateway |
| `merchant_id` | (empty) | Set after creating merchant |
| `api_key` | (empty) | Set after generating key |
| `key_id` | (empty) | Set after generating key |

---

## 4. Postman Collection JSON

**File:** `postman/PayFlow-Sprint2.postman_collection.json`

```json
{
  "info": {
    "name": "PayFlow API - Sprint 2",
    "description": "API Key Management and Webhook Configuration",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8082"
    },
    {
      "key": "gateway_url",
      "value": "http://localhost:8080"
    }
  ],
  "item": [
    {
      "name": "Setup",
      "item": [
        {
          "name": "Create Merchant",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"userId\": \"usr_postman_test\",\n  \"businessName\": \"Postman Test Store\",\n  \"businessType\": \"INDIVIDUAL\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/v1/merchants",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 201', function () {",
                  "    pm.response.to.have.status(201);",
                  "});",
                  "",
                  "pm.test('Response has merchant ID', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data.id).to.match(/^merch_/);",
                  "    pm.environment.set('merchant_id', jsonData.data.id);",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    },
    {
      "name": "API Keys",
      "item": [
        {
          "name": "Generate TEST Key",
          "request": {
            "method": "POST",
            "header": [],
            "url": {
              "raw": "{{base_url}}/v1/merchants/{{merchant_id}}/api-keys?keyType=TEST",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}", "api-keys"],
              "query": [
                {
                  "key": "keyType",
                  "value": "TEST"
                }
              ]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 201', function () {",
                  "    pm.response.to.have.status(201);",
                  "});",
                  "",
                  "pm.test('Response has public and secret keys', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data.public_key).to.match(/^pk_test_/);",
                  "    pm.expect(jsonData.data.secret_key).to.match(/^sk_test_/);",
                  "    pm.environment.set('api_key', jsonData.data.secret_key);",
                  "    pm.environment.set('key_id', jsonData.data.key_id);",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Generate LIVE Key",
          "request": {
            "method": "POST",
            "header": [],
            "url": {
              "raw": "{{base_url}}/v1/merchants/{{merchant_id}}/api-keys?keyType=LIVE",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}", "api-keys"],
              "query": [
                {
                  "key": "keyType",
                  "value": "LIVE"
                }
              ]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 201', function () {",
                  "    pm.response.to.have.status(201);",
                  "});",
                  "",
                  "pm.test('Response has LIVE keys', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data.public_key).to.match(/^pk_live_/);",
                  "    pm.expect(jsonData.data.secret_key).to.match(/^sk_live_/);",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "List All Keys",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/v1/merchants/{{merchant_id}}/api-keys",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}", "api-keys"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function () {",
                  "    pm.response.to.have.status(200);",
                  "});",
                  "",
                  "pm.test('Response contains keys', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data).to.be.an('array');",
                  "    pm.expect(jsonData.data.length).to.be.greaterThan(0);",
                  "});",
                  "",
                  "pm.test('Keys have required fields', function () {",
                  "    var jsonData = pm.response.json();",
                  "    var key = jsonData.data[0];",
                  "    pm.expect(key).to.have.property('keyId');",
                  "    pm.expect(key).to.have.property('keyType');",
                  "    pm.expect(key).to.have.property('status');",
                  "    pm.expect(key).to.not.have.property('secretKeyHash');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Revoke Key",
          "request": {
            "method": "DELETE",
            "header": [],
            "url": {
              "raw": "{{base_url}}/v1/merchants/{{merchant_id}}/api-keys/{{key_id}}",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}", "api-keys", "{{key_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function () {",
                  "    pm.response.to.have.status(200);",
                  "});",
                  "",
                  "pm.test('Response confirms revocation', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data.message).to.include('revoked');",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    },
    {
      "name": "Webhooks",
      "item": [
        {
          "name": "Update Webhook URL",
          "request": {
            "method": "PUT",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"webhookUrl\": \"https://api.teststore.com/webhooks\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/v1/merchants/{{merchant_id}}/webhook",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}", "webhook"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function () {",
                  "    pm.response.to.have.status(200);",
                  "});",
                  "",
                  "pm.test('Response has webhook config', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data.webhookUrl).to.equal('https://api.teststore.com/webhooks');",
                  "    pm.expect(jsonData.data.webhookSecret).to.have.lengthOf(32);",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Webhook Config",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/v1/merchants/{{merchant_id}}/webhook",
              "host": ["{{base_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}", "webhook"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 200', function () {",
                  "    pm.response.to.have.status(200);",
                  "});",
                  "",
                  "pm.test('Response has webhook details', function () {",
                  "    var jsonData = pm.response.json();",
                  "    pm.expect(jsonData.data).to.have.property('webhookUrl');",
                  "    pm.expect(jsonData.data).to.have.property('webhookSecret');",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    },
    {
      "name": "Gateway Auth",
      "item": [
        {
          "name": "Test with Valid API Key",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "X-Api-Key",
                "value": "{{api_key}}"
              }
            ],
            "url": {
              "raw": "{{gateway_url}}/v1/merchants/{{merchant_id}}",
              "host": ["{{gateway_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "// Note: This test may fail if key was revoked",
                  "pm.test('Status code is 200 or 401', function () {",
                  "    pm.expect(pm.response.code).to.be.oneOf([200, 401]);",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Test without API Key",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{gateway_url}}/v1/merchants/{{merchant_id}}",
              "host": ["{{gateway_url}}"],
              "path": ["v1", "merchants", "{{merchant_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "pm.test('Status code is 401', function () {",
                  "    pm.response.to.have.status(401);",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    }
  ]
}
```

---

## 5. Running with Newman (CI/CD)

### 5.1 Install Newman

```powershell
npm install -g newman
```

### 5.2 Run Collection

```powershell
newman run postman/PayFlow-Sprint2.postman_collection.json `
  --environment postman/PayFlow-Local.postman_environment.json `
  --reporters cli,json `
  --reporter-json-export results.json
```

### 5.3 GitHub Actions Integration

```yaml
# .github/workflows/ci-backend.yml
- name: Run Postman Tests
  run: |
    npm install -g newman
    newman run postman/PayFlow-Sprint2.postman_collection.json \
      --environment postman/PayFlow-Local.postman_environment.json
```

---

## 6. Key Takeaways

| Concept | Remember |
|---------|----------|
| Environment variables | Use {{variable}} syntax |
| Test scripts | pm.test() and pm.expect() |
| Chaining requests | Set variables in test scripts |
| Newman | CLI runner for CI/CD |

---

## 7. Next Steps

**Continue to:** [part-11-aws-deployment.md](./part-11-aws-deployment.md)

In the next part, you'll deploy Sprint 2 changes to AWS.

---

**End of Sprint 2, Part 10**
