# Sprint 1: Auth & Onboarding — Tasks

**Sprint Duration:** 2 weeks  
**Total Parts:** 6 implementation parts

---

## Task Overview

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 01 | Service Registry & Config Server | 3-4 hours | ⬜ |
| 02 | API Gateway | 3-4 hours | ⬜ |
| 03 | Identity Service | 4-5 hours | ⬜ |
| 04 | Merchant Service | 3-4 hours | ⬜ |
| 05 | React Frontend | 4-5 hours | ⬜ |
| 06 | Docker & CI/CD | 3-4 hours | ⬜ |

---

## Part 01: Service Registry & Config Server

**File:** [implementation/part-01-service-registry-config.md](./implementation/part-01-service-registry-config.md)

### Tasks

- [ ] Create service-registry module folder
- [ ] Create service-registry/pom.xml with Eureka Server dependency
- [ ] Create EurekaServerApplication.java
- [ ] Create application.yml with Eureka configuration
- [ ] Start and verify Eureka dashboard at http://localhost:8761
- [ ] Create config-server module folder
- [ ] Create config-server/pom.xml with Config Server dependency
- [ ] Create ConfigServerApplication.java
- [ ] Create application.yml with config location
- [ ] Create config/ folder with application.yml (shared config)
- [ ] Start and verify Config Server at http://localhost:8888

### Verification

```powershell
# Start Service Registry
cd service-registry
mvn spring-boot:run

# Open browser: http://localhost:8761
# Should see Eureka dashboard

# Start Config Server
cd config-server
mvn spring-boot:run

# Test config endpoint
curl http://localhost:8888/application/default
# Should return shared configuration
```

---

## Part 02: API Gateway

**File:** [implementation/part-02-api-gateway.md](./implementation/part-02-api-gateway.md)

### Tasks

- [ ] Create api-gateway module folder
- [ ] Create api-gateway/pom.xml with Gateway dependencies
- [ ] Create ApiGatewayApplication.java
- [ ] Create application.yml with routes
- [ ] Create JwtAuthenticationFilter.java
- [ ] Create JwtUtil.java for token validation
- [ ] Generate RSA key pair for JWT
- [ ] Configure public routes (no auth required)
- [ ] Configure protected routes (auth required)
- [ ] Add rate limiting filter
- [ ] Add correlation ID filter
- [ ] Verify gateway routes working

### Verification

```powershell
# Start Gateway
cd api-gateway
mvn spring-boot:run

# Test health endpoint
curl http://localhost:8080/actuator/health

# Verify it registers with Eureka
# Check http://localhost:8761 - should show API-GATEWAY
```

---

## Part 03: Identity Service

**File:** [implementation/part-03-identity-service.md](./implementation/part-03-identity-service.md)

### Tasks

- [ ] Create identity-service module folder
- [ ] Create identity-service/pom.xml
- [ ] Create IdentityServiceApplication.java
- [ ] Create User.java entity
- [ ] Create UserRepository.java
- [ ] Create DTOs (RegisterRequest, LoginRequest, AuthResponse)
- [ ] Create JwtTokenProvider.java
- [ ] Create AuthService.java and implementation
- [ ] Create AuthController.java
- [ ] Configure Spring Security
- [ ] Configure password encoder (BCrypt)
- [ ] Add database migration (users table)
- [ ] Write unit tests for AuthService
- [ ] Write integration tests for AuthController
- [ ] Verify registration and login flows

### Verification

```powershell
# Start Identity Service
cd identity-service
mvn spring-boot:run

# Test registration
curl -X POST http://localhost:8080/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!","fullName":"Test User"}'

# Test login
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!"}'

# Should return JWT token
```

---

## Part 04: Merchant Service

**File:** [implementation/part-04-merchant-service.md](./implementation/part-04-merchant-service.md)

### Tasks

- [ ] Create merchant-service module folder
- [ ] Create merchant-service/pom.xml
- [ ] Create MerchantServiceApplication.java
- [ ] Create Merchant.java entity
- [ ] Create MerchantRepository.java
- [ ] Create DTOs (CreateMerchantRequest, MerchantResponse)
- [ ] Create IdGenerator for merchant IDs (mer_xxxx)
- [ ] Create MerchantService.java and implementation
- [ ] Create MerchantController.java
- [ ] Add database migration (merchants table)
- [ ] Write unit tests for MerchantService
- [ ] Write integration tests for MerchantController
- [ ] Verify merchant creation with JWT

### Verification

```powershell
# Start Merchant Service
cd merchant-service
mvn spring-boot:run

# Create merchant (with JWT from login)
curl -X POST http://localhost:8080/v1/merchants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{"businessName":"Acme Store","businessType":"COMPANY","country":"IN"}'

# Get merchant profile
curl http://localhost:8080/v1/merchants/me \
  -H "Authorization: Bearer <your-jwt-token>"
```

---

## Part 05: React Frontend

**File:** [implementation/part-05-react-frontend.md](./implementation/part-05-react-frontend.md)

### Tasks

- [ ] Create merchant-portal folder
- [ ] Initialize React project with Vite
- [ ] Install dependencies (axios, react-router, zustand, tailwind)
- [ ] Configure TypeScript
- [ ] Configure Tailwind CSS
- [ ] Create folder structure (pages, components, api, store)
- [ ] Create API client with axios
- [ ] Create auth store (zustand)
- [ ] Create Login page
- [ ] Create Register page
- [ ] Create Dashboard layout
- [ ] Create Protected Route component
- [ ] Create Merchant Onboarding page
- [ ] Add form validation
- [ ] Test login/register flow in browser

### Verification

```powershell
# Start frontend
cd merchant-portal
npm install
npm run dev

# Open browser: http://localhost:5173
# Test registration and login
# Verify redirect to dashboard
```

---

## Part 06: Docker & CI/CD

**File:** [implementation/part-06-docker-cicd.md](./implementation/part-06-docker-cicd.md)

### Tasks

- [ ] Create Dockerfile for service-registry
- [ ] Create Dockerfile for config-server
- [ ] Create Dockerfile for api-gateway
- [ ] Create Dockerfile for identity-service
- [ ] Create Dockerfile for merchant-service
- [ ] Update docker-compose.yml with all services
- [ ] Configure service dependencies and health checks
- [ ] Create .github/workflows/ci-backend.yml
- [ ] Add build job (mvn test)
- [ ] Add Docker build job
- [ ] Create .github/workflows/ci-frontend.yml
- [ ] Add npm test job
- [ ] Verify full stack starts with docker compose up
- [ ] Run CI pipeline tests

### Verification

```powershell
# Build all services
mvn clean package -DskipTests

# Start full stack
docker compose up -d

# Check all services
docker ps
# Should show: postgres, redis, localstack, 
#              service-registry, config-server, api-gateway,
#              identity-service, merchant-service

# Test through gateway
curl http://localhost:8080/actuator/health
```

---

## Sprint Completion Checklist

### Services Running
- [ ] Service Registry at http://localhost:8761
- [ ] Config Server at http://localhost:8888
- [ ] API Gateway at http://localhost:8080
- [ ] Identity Service at http://localhost:8081
- [ ] Merchant Service at http://localhost:8082
- [ ] All services registered in Eureka

### APIs Working
- [ ] POST /v1/auth/register returns JWT
- [ ] POST /v1/auth/login returns JWT
- [ ] GET /v1/auth/me returns user profile
- [ ] POST /v1/merchants creates merchant
- [ ] GET /v1/merchants/me returns merchant

### Frontend Working
- [ ] Registration form submits successfully
- [ ] Login redirects to dashboard
- [ ] JWT stored in localStorage
- [ ] Protected routes redirect to login
- [ ] Merchant onboarding form works

### Docker & CI/CD
- [ ] All Dockerfiles build successfully
- [ ] docker compose up starts all services
- [ ] GitHub Actions CI passes

---

## Time Estimate

| Part | Estimated Time |
|------|----------------|
| Part 01 | 3-4 hours |
| Part 02 | 3-4 hours |
| Part 03 | 4-5 hours |
| Part 04 | 3-4 hours |
| Part 05 | 4-5 hours |
| Part 06 | 3-4 hours |
| **Total** | **20-26 hours** |

---

## Implementation Order

```
Start
  │
  ▼
Part 01: Service Registry & Config Server
  │ (foundation for service discovery)
  ▼
Part 02: API Gateway
  │ (entry point with JWT validation)
  ▼
Part 03: Identity Service
  │ (authentication ready)
  ▼
Part 04: Merchant Service
  │ (merchant onboarding ready)
  ▼
Part 05: React Frontend
  │ (user interface ready)
  ▼
Part 06: Docker & CI/CD
  │ (containerized deployment)
  ▼
Sprint 1 Complete! 🎉
  │
  ▼
Continue to Sprint 2
```

---

## Dependencies Graph

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SERVICE DEPENDENCIES                                   │
│                                                                              │
│                    ┌─────────────────────┐                                  │
│                    │  Service Registry   │                                  │
│                    │      (Eureka)       │                                  │
│                    └──────────┬──────────┘                                  │
│                               │                                              │
│                    ┌──────────┴──────────┐                                  │
│                    │                     │                                  │
│                    ▼                     ▼                                  │
│         ┌─────────────────┐   ┌─────────────────┐                          │
│         │  Config Server  │   │   API Gateway   │                          │
│         └────────┬────────┘   └────────┬────────┘                          │
│                  │                     │                                    │
│                  └──────────┬──────────┘                                    │
│                             │                                                │
│              ┌──────────────┼──────────────┐                                │
│              │              │              │                                │
│              ▼              ▼              ▼                                │
│     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                        │
│     │  Identity   │ │  Merchant   │ │   Future    │                        │
│     │  Service    │ │  Service    │ │  Services   │                        │
│     └──────┬──────┘ └──────┬──────┘ └─────────────┘                        │
│            │               │                                                │
│            └───────┬───────┘                                                │
│                    │                                                        │
│                    ▼                                                        │
│            ┌─────────────┐                                                  │
│            │ PostgreSQL  │                                                  │
│            └─────────────┘                                                  │
│                                                                              │
│  Start Order:                                                                │
│  1. PostgreSQL, Redis (from Sprint 0)                                       │
│  2. Service Registry                                                         │
│  3. Config Server                                                            │
│  4. API Gateway                                                              │
│  5. Identity Service, Merchant Service (parallel)                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Start Implementation:** [implementation/part-01-service-registry-config.md](./implementation/part-01-service-registry-config.md)
