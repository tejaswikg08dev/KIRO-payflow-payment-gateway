# Sprint 2, Part 09: Docker Configuration Update

**Duration:** 30 minutes  
**Prerequisites:** Part 08 completed  
**Goal:** Verify Docker configuration supports Sprint 2 changes

---

## 1. Learning Objectives

By the end of this part, you will:
- Verify existing Docker configuration is sufficient for Sprint 2
- Understand service dependencies for API key authentication
- Test the complete stack with Docker Compose

---

## 2. Sprint 2 Impact on Docker

### 2.1 No New Services Required

Sprint 2 changes are within existing services:
- **api-gateway**: Added `ApiKeyAuthFilter` (no config changes needed)
- **merchant-service**: Added endpoints (no config changes needed)
- **frontend-dashboard**: Added `ApiKeysPage` (no config changes needed)

### 2.2 Dependencies Verification

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE DEPENDENCIES FOR API KEY AUTH                     │
│                                                                              │
│  api-gateway                                                                 │
│       │                                                                      │
│       ├── Redis (cache for API key validation)         ✅ Already in compose│
│       │                                                                      │
│       └── merchant-service (validate key via WebClient) ✅ Already in compose│
│                                                                              │
│  merchant-service                                                            │
│       │                                                                      │
│       └── PostgreSQL (api_keys table)                  ✅ Already in compose│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Existing docker-compose.yml Review

**File:** `docker-compose.yml` (main application services)

```yaml
# Services already configured from Sprint 1:

services:
  service-registry:
    build: ./service-registry
    ports:
      - "8761:8761"
    
  api-gateway:
    build: ./api-gateway
    ports:
      - "8080:8080"
    depends_on:
      - service-registry
      - redis
    environment:
      - SPRING_DATA_REDIS_HOST=redis
      
  merchant-service:
    build: ./merchant-service
    ports:
      - "8082:8082"
    depends_on:
      - service-registry
      - postgres
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/payflow
      
  frontend-dashboard:
    build: ./frontend-dashboard
    ports:
      - "3000:80"
    depends_on:
      - api-gateway
```

**File:** `docker-compose-infra.yml` (infrastructure only)

```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: payflow
      POSTGRES_USER: payflow_user
      POSTGRES_PASSWORD: payflow_secret
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
```

---

## 4. Testing with Docker

### 4.1 Start Infrastructure

```powershell
docker compose -f docker-compose-infra.yml up -d
```

### 4.2 Verify Redis is Running

```powershell
docker exec -it payflow-redis redis-cli ping
# Expected: PONG
```

### 4.3 Build and Start All Services

```powershell
docker compose up --build -d
```

### 4.4 Check Service Health

```powershell
# Check all containers are running
docker compose ps

# Check logs for errors
docker compose logs api-gateway --tail 50
docker compose logs merchant-service --tail 50
```

---

## 5. Test API Key Authentication in Docker

### 5.1 Generate API Key

```powershell
# Create merchant
curl -X POST http://localhost:8082/v1/merchants `
  -H "Content-Type: application/json" `
  -d '{"userId":"docker_test","businessName":"Docker Test","businessType":"INDIVIDUAL"}'

# Generate key (replace merch_xxx with actual ID)
curl -X POST "http://localhost:8082/v1/merchants/merch_xxx/api-keys?keyType=TEST"
```

### 5.2 Test via Gateway

```powershell
# With valid key (via gateway port 8080)
curl http://localhost:8080/v1/merchants/merch_xxx `
  -H "X-Api-Key: sk_test_xxxxxxxx"
# Expected: 200 OK

# Without key
curl http://localhost:8080/v1/merchants/merch_xxx
# Expected: 401 Unauthorized
```

### 5.3 Verify Redis Caching

```powershell
# Check Redis for cached key
docker exec -it payflow-redis redis-cli keys "apikey:*"
# Expected: Shows cached key hash
```

---

## 6. Environment Variables

No new environment variables required for Sprint 2. Existing variables:

| Variable | Service | Default |
|----------|---------|---------|
| `SPRING_DATA_REDIS_HOST` | api-gateway | redis |
| `SPRING_DATA_REDIS_PORT` | api-gateway | 6379 |
| `SPRING_DATASOURCE_URL` | merchant-service | jdbc:postgresql://postgres:5432/payflow |

---

## 7. Troubleshooting

### Issue: "Connection refused" to Redis

**Solution:**
```powershell
# Ensure Redis is running
docker compose -f docker-compose-infra.yml up redis -d

# Verify connection
docker exec -it payflow-redis redis-cli ping
```

### Issue: Gateway can't reach merchant-service

**Solution:**
```powershell
# For Docker networking, update ApiKeyAuthFilter to use service name
# Change: http://localhost:8082
# To:     http://merchant-service:8082
```

### Issue: Database connection error

**Solution:**
```powershell
# Ensure PostgreSQL is running
docker compose -f docker-compose-infra.yml up postgres -d

# Check database exists
docker exec -it payflow-postgres psql -U payflow_user -d payflow -c "\dt merchant.*"
```

---

## 8. Key Takeaways

| Aspect | Status |
|--------|--------|
| New Docker services | Not needed |
| Redis dependency | Already configured |
| PostgreSQL dependency | Already configured |
| Network configuration | Already correct |

---

## 9. Next Steps

**Continue to:** [part-10-cicd-postman.md](./part-10-cicd-postman.md)

In the next part, you'll create Postman tests for CI/CD integration.

---

**End of Sprint 2, Part 09**
