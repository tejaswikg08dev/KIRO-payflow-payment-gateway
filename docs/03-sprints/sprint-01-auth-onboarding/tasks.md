# Sprint 1: Auth & Onboarding — Tasks

**Sprint Duration:** 2 weeks  
**Total Parts:** 24 implementation parts

---

## Task Overview

### Infrastructure & Gateway (Parts 01-03)

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 01 | Service Registry (Eureka) | 1-2 hours | ⬜ |
| 02 | Config Server | 1-2 hours | ⬜ |
| 03 | API Gateway | 2-3 hours | ⬜ |

### Identity Service (Parts 04-09)

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 04 | Identity Service Setup | 1-2 hours | ⬜ |
| 05 | Identity Database (Flyway) | 1-2 hours | ⬜ |
| 06 | JWT Authentication | 2-3 hours | ⬜ |
| 07 | Identity Controllers | 1-2 hours | ⬜ |
| 08 | Identity Swagger | 30 min | ⬜ |
| 09 | Identity Testing | 1-2 hours | ⬜ |

### Merchant Service (Parts 10-13)

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 10 | Merchant Service Setup | 1-2 hours | ⬜ |
| 11 | Merchant Database (Flyway) | 1-2 hours | ⬜ |
| 12 | Merchant Registration | 1-2 hours | ⬜ |
| 13 | Merchant Swagger Testing | 30 min | ⬜ |

### Frontend Dashboard (Parts 14-17)

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 14 | Frontend Dashboard Setup | 1-2 hours | ⬜ |
| 15 | Frontend Login Page | 1-2 hours | ⬜ |
| 16 | Frontend Dashboard Page | 1-2 hours | ⬜ |
| 17 | Frontend Transactions Page | 1-2 hours | ⬜ |

### DevOps & Deployment (Parts 18-21)

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 18 | Docker Services | 1-2 hours | ⬜ |
| 19 | CI/CD Backend | 1-2 hours | ⬜ |
| 20 | AWS VPC & RDS | 1-2 hours | ⬜ |
| 21 | AWS Deployment | 1-2 hours | ⬜ |

### Testing & Documentation (Parts 22-24)

| Part | Name | Duration | Status |
|------|------|----------|--------|
| 22 | E2E Testing | 1-2 hours | ⬜ |
| 23 | Git & PR | 30 min | ⬜ |
| 24 | Sprint Summary | 30 min | ⬜ |

---

## Part 01: Service Registry (Eureka)

**File:** [implementation/part-01-service-registry.md](./implementation/part-01-service-registry.md)

### Tasks

- [ ] Create service-registry module folder
- [ ] Create service-registry/pom.xml with Eureka Server dependency
- [ ] Create ServiceRegistryApplication.java with @EnableEurekaServer
- [ ] Create application.yml with Eureka configuration
- [ ] Configure port 8761
- [ ] Disable self-registration
- [ ] Create Dockerfile for service-registry
- [ ] Start and verify Eureka dashboard

### Verification

```powershell
cd service-registry
mvn spring-boot:run
# Open browser: http://localhost:8761
# Should see Eureka dashboard
```

---

## Part 02: Config Server

**File:** [implementation/part-02-config-server.md](./implementation/part-02-config-server.md)

### Tasks

- [ ] Create config-server module folder
- [ ] Create config-server/pom.xml with Config Server dependency
- [ ] Create ConfigServerApplication.java with @EnableConfigServer
- [ ] Create application.yml with config location
- [ ] Configure port 8888
- [ ] Create config/ folder for centralized configs
- [ ] Create config/application.yml (shared config)
- [ ] Register with Eureka
- [ ] Create Dockerfile for config-server
- [ ] Start and verify Config Server

### Verification

```powershell
cd config-server
mvn spring-boot:run
# Test config endpoint
curl http://localhost:8888/application/default
# Should return shared configuration
```

---

## Part 03: API Gateway

**File:** [implementation/part-03-api-gateway.md](./implementation/part-03-api-gateway.md)

### Tasks

- [ ] Create api-gateway module folder
- [ ] Create api-gateway/pom.xml with Gateway dependencies
- [ ] Create ApiGatewayApplication.java
- [ ] Create application.yml with routes configuration
- [ ] Configure routes for identity-service (/v1/auth/**)
- [ ] Configure routes for merchant-service (/v1/merchants/**)
- [ ] Create CorrelationIdFilter.java (order: -2)
- [ ] Create RateLimitFilter.java (order: -1)
- [ ] Configure public routes (no auth required)
- [ ] Register with Eureka
- [ ] Create Dockerfile for api-gateway
- [ ] Verify gateway routes working

### Verification

```powershell
cd api-gateway
mvn spring-boot:run
# Test health endpoint
curl http://localhost:8080/actuator/health
# Check Eureka: http://localhost:8761 - should show API-GATEWAY
```

---

## Part 04: Identity Service Setup

**File:** [implementation/part-04-identity-service-setup.md](./implementation/part-04-identity-service-setup.md)

### Tasks

- [ ] Create identity-service module folder
- [ ] Create identity-service/pom.xml with dependencies
- [ ] Create IdentityServiceApplication.java
- [ ] Create application.yml with database config
- [ ] Configure port 8081
- [ ] Configure PostgreSQL connection (identity schema)
- [ ] Register with Eureka
- [ ] Create Dockerfile for identity-service

### Verification

```powershell
cd identity-service
mvn spring-boot:run
# Check Eureka: should show IDENTITY-SERVICE
```

---

## Part 05: Identity Database (Flyway)

**File:** [implementation/part-05-identity-database.md](./implementation/part-05-identity-database.md)

### Tasks

- [ ] Add Flyway dependency to pom.xml
- [ ] Create src/main/resources/db/migration folder
- [ ] Create V1__create_users_table.sql
- [ ] Create User.java entity in model/ package
- [ ] Create UserRepository.java in repository/ package
- [ ] Configure Flyway for identity schema
- [ ] Verify migration runs on startup

### Database Schema

```sql
CREATE TABLE identity.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MERCHANT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Verification

```powershell
# Check migration applied
docker exec payflow-postgres psql -U postgres -d payflow -c "\dt identity.*"
# Should show: users table
```

---

## Part 06: JWT Authentication

**File:** [implementation/part-06-jwt-authentication.md](./implementation/part-06-jwt-authentication.md)

### Tasks

- [ ] Configure JWT secret in application.yml (256+ bit secret)
- [ ] Create JwtService.java in service/ package
- [ ] Implement generateAccessToken() method
- [ ] Implement generateRefreshToken() method
- [ ] Implement validateToken() method
- [ ] Implement extractUserId() method
- [ ] Configure token expiration (15 min access, 7 days refresh)
- [ ] Create SecurityConfig.java
- [ ] Configure BCrypt password encoder
- [ ] Configure public endpoints

### Verification

```powershell
# Verify JWT configuration exists in application.yml
# jwt.secret should be at least 32 characters for HS256
```

---

## Part 07: Identity Controllers

**File:** [implementation/part-07-identity-controllers.md](./implementation/part-07-identity-controllers.md)

### Tasks

- [ ] Create DTOs in dto/ package:
  - [ ] RegisterRequest.java
  - [ ] LoginRequest.java
  - [ ] AuthResponse.java
  - [ ] UserResponse.java
- [ ] Create AuthService.java interface in service/ package
- [ ] Create AuthServiceImpl.java with:
  - [ ] register() method
  - [ ] login() method
  - [ ] getCurrentUser() method
- [ ] Create AuthController.java with:
  - [ ] POST /v1/auth/register
  - [ ] POST /v1/auth/login
  - [ ] GET /v1/auth/me

### Verification

```powershell
# Test registration
curl -X POST http://localhost:8081/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!","fullName":"Test User"}'

# Test login
curl -X POST http://localhost:8081/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!"}'
```

---

## Part 08: Identity Swagger

**File:** [implementation/part-08-identity-swagger.md](./implementation/part-08-identity-swagger.md)

### Tasks

- [ ] Add springdoc-openapi dependency
- [ ] Create OpenApiConfig.java
- [ ] Add @Operation annotations to controllers
- [ ] Add @Schema annotations to DTOs
- [ ] Configure Swagger UI path
- [ ] Test Swagger UI in browser

### Verification

```powershell
# Open browser: http://localhost:8081/swagger-ui/index.html
# Should see Identity Service API documentation
```

---

## Part 09: Identity Testing

**File:** [implementation/part-09-identity-testing.md](./implementation/part-09-identity-testing.md)

### Tasks

- [ ] Add test dependencies (spring-boot-starter-test, H2)
- [ ] Create AuthServiceTest.java (unit tests)
- [ ] Create AuthControllerTest.java (integration tests)
- [ ] Test registration flow
- [ ] Test login flow
- [ ] Test validation errors
- [ ] Test duplicate email handling
- [ ] Achieve >80% code coverage

### Verification

```powershell
cd identity-service
mvn test
# All tests should pass
```

---

## Part 10: Merchant Service Setup

**File:** [implementation/part-10-merchant-service-setup.md](./implementation/part-10-merchant-service-setup.md)

### Tasks

- [ ] Create merchant-service module folder
- [ ] Create merchant-service/pom.xml with dependencies
- [ ] Create MerchantServiceApplication.java
- [ ] Create application.yml with database config
- [ ] Configure port 8082
- [ ] Configure PostgreSQL connection (merchant schema)
- [ ] Register with Eureka
- [ ] Create Dockerfile for merchant-service

### Verification

```powershell
cd merchant-service
mvn spring-boot:run
# Check Eureka: should show MERCHANT-SERVICE
```

---

## Part 11: Merchant Database (Flyway)

**File:** [implementation/part-11-merchant-database.md](./implementation/part-11-merchant-database.md)

### Tasks

- [ ] Add Flyway dependency to pom.xml
- [ ] Create src/main/resources/db/migration folder
- [ ] Create V1__create_merchants_table.sql
- [ ] Create V2__create_api_keys_table.sql
- [ ] Create Merchant.java entity in model/ package
- [ ] Create ApiKey.java entity in model/ package
- [ ] Create MerchantRepository.java
- [ ] Create ApiKeyRepository.java
- [ ] Configure Flyway for merchant schema
- [ ] Verify migrations run on startup

### Database Schema

```sql
-- V1: Merchants table
CREATE TABLE merchant.merchants (
    id VARCHAR(50) PRIMARY KEY,
    user_id UUID NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'IN',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(255),
    mdr_percentage DECIMAL(5,2) DEFAULT 2.00,
    settlement_schedule VARCHAR(20) DEFAULT 'T+2',
    kyc_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- V2: API Keys table
CREATE TABLE merchant.api_keys (
    id VARCHAR(50) PRIMARY KEY,
    merchant_id VARCHAR(50) NOT NULL REFERENCES merchant.merchants(id),
    key_type VARCHAR(10) NOT NULL,
    public_key VARCHAR(100) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Verification

```powershell
# Check migrations applied
docker exec payflow-postgres psql -U postgres -d payflow -c "\dt merchant.*"
# Should show: merchants, api_keys tables
```

---

## Part 12: Merchant Registration

**File:** [implementation/part-12-merchant-registration.md](./implementation/part-12-merchant-registration.md)

### Tasks

- [ ] Create DTOs in dto/ package:
  - [ ] CreateMerchantRequest.java
  - [ ] MerchantResponse.java
- [ ] Create MerchantService.java with:
  - [ ] createMerchant() method
  - [ ] getMerchant() method
  - [ ] getMerchantByUserId() method
  - [ ] generateApiKey() method
  - [ ] validateSecretKey() method
- [ ] Create MerchantController.java with:
  - [ ] POST /v1/merchants
  - [ ] GET /v1/merchants/{merchantId}
  - [ ] GET /v1/merchants/me
  - [ ] POST /v1/merchants/{merchantId}/api-keys

### Verification

```powershell
# Create merchant (with JWT from login)
curl -X POST http://localhost:8082/v1/merchants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{"businessName":"Acme Store","businessType":"COMPANY"}'
```

---

## Part 13: Merchant Swagger Testing

**File:** [implementation/part-13-merchant-swagger-testing.md](./implementation/part-13-merchant-swagger-testing.md)

### Tasks

- [ ] Add springdoc-openapi dependency
- [ ] Create OpenApiConfig.java
- [ ] Add @Operation annotations to controllers
- [ ] Test all merchant endpoints via Swagger
- [ ] Verify API key generation works
- [ ] Document API responses

### Verification

```powershell
# Open browser: http://localhost:8082/swagger-ui/index.html
# Test: Create merchant, Generate API key, List keys
```

---

## Part 14: Frontend Dashboard Setup

**File:** [implementation/part-14-frontend-dashboard-setup.md](./implementation/part-14-frontend-dashboard-setup.md)

### Tasks

- [ ] Create frontend-dashboard folder
- [ ] Initialize React project with Vite
- [ ] Install dependencies:
  - [ ] axios
  - [ ] react-router-dom
  - [ ] zustand (state management)
  - [ ] tailwindcss
- [ ] Configure TypeScript
- [ ] Configure Tailwind CSS
- [ ] Create folder structure:
  - [ ] src/pages/
  - [ ] src/components/
  - [ ] src/api/
  - [ ] src/store/
  - [ ] src/hooks/
- [ ] Create API client with axios
- [ ] Create auth store (zustand)

### Verification

```powershell
cd frontend-dashboard
npm install
npm run dev
# Open browser: http://localhost:3000
```

---

## Part 15: Frontend Login & Registration Pages

**File:** [implementation/part-15-frontend-login-page.md](./implementation/part-15-frontend-login-page.md)

### Tasks

- [x] Create LoginPage.tsx component
- [x] Create RegisterPage.tsx component
- [x] Create MerchantOnboardingPage.tsx component
- [x] Implement login form with validation
- [x] Implement register form with validation
- [x] Implement merchant onboarding form
- [x] Store JWT token in localStorage (key: `payflow_token`)
- [x] Handle login/register errors with user-friendly messages
- [x] Add navigation links between login and register
- [x] Configure React Router routes
- [x] Configure Vite proxy for API calls

### Implemented Routes

| Route | Page | Description |
|-------|------|-------------|
| `/login` | LoginPage | User login form |
| `/register` | RegisterPage | User registration form |
| `/onboarding` | MerchantOnboardingPage | Business setup for new merchants |

### User Flow

```
/register → (creates user account) → /onboarding → (creates merchant) → /dashboard
       ↑                                                                      │
       └── /login ← (existing user) ─────────────────────────────────────────┘
```

### Verification

```powershell
# Start frontend
cd frontend-dashboard
npm run dev

# Test the complete flow:
# 1. Visit http://localhost:3000/register - Create new user
# 2. After registration, auto-redirect to /onboarding
# 3. Complete merchant setup (business name, type, etc.)
# 4. Redirect to /dashboard
# 5. Logout and visit /login - Login with existing user
```

---

## Part 16: Frontend Dashboard Page

**File:** [implementation/part-16-frontend-dashboard-page.md](./implementation/part-16-frontend-dashboard-page.md)

### Tasks

- [ ] Create DashboardLayout.tsx with sidebar
- [ ] Create DashboardPage.tsx component
- [ ] Create Header component with user menu
- [ ] Create Sidebar component with navigation
- [ ] Display merchant info on dashboard
- [ ] Add logout functionality
- [ ] Add "Manage API Keys" button (links to Sprint 2)

### Verification

```powershell
# Login and verify:
# - Dashboard displays merchant info
# - Sidebar navigation works
# - Logout redirects to login
```

---

## Part 17: Frontend Transactions Page

**File:** [implementation/part-17-frontend-transactions-page.md](./implementation/part-17-frontend-transactions-page.md)

### Tasks

- [ ] Create TransactionsPage.tsx component
- [ ] Create TransactionTable component
- [ ] Create TransactionFilters component
- [ ] Display empty state (no transactions yet)
- [ ] Add placeholder for transaction data
- [ ] Configure route /transactions

### Verification

```powershell
# Navigate to /transactions
# Should show transactions page (empty state for now)
```

---

## Part 18: Docker Services

**File:** [implementation/part-18-docker-services.md](./implementation/part-18-docker-services.md)

### Tasks

- [ ] Create Dockerfile for each service:
  - [ ] service-registry/Dockerfile
  - [ ] config-server/Dockerfile
  - [ ] api-gateway/Dockerfile
  - [ ] identity-service/Dockerfile
  - [ ] merchant-service/Dockerfile
- [ ] Create docker-compose.yml with all services
- [ ] Configure service dependencies
- [ ] Configure health checks
- [ ] Configure environment variables
- [ ] Test full stack with docker compose up

### Verification

```powershell
# Build all services
mvn clean package -DskipTests

# Start full stack
docker compose up -d

# Check all services
docker ps
# Should show all services running

# Test through gateway
curl http://localhost:8080/actuator/health
```

---

## Part 19: CI/CD Backend

**File:** [implementation/part-19-cicd-backend.md](./implementation/part-19-cicd-backend.md)

### Tasks

- [ ] Create .github/workflows/ci-backend.yml
- [ ] Add Maven build job
- [ ] Add unit test job
- [ ] Add Docker build job
- [ ] Configure branch triggers (main, develop, PR)
- [ ] Add code coverage reporting (optional)
- [ ] Create .github/workflows/ci-frontend.yml
- [ ] Add npm install job
- [ ] Add npm test job
- [ ] Add npm build job

### Verification

```powershell
# Push to GitHub
# Check Actions tab for CI workflow
# Verify all jobs pass
```

---

## Part 20: AWS VPC & RDS (Optional)

**File:** [implementation/part-20-aws-vpc-rds.md](./implementation/part-20-aws-vpc-rds.md)

### Tasks

- [ ] Create VPC with public/private subnets
- [ ] Create RDS PostgreSQL instance
- [ ] Configure security groups
- [ ] Create ElastiCache Redis cluster (optional)
- [ ] Document connection strings
- [ ] Update application configs for AWS

### Verification

```powershell
# Test RDS connection
psql -h <rds-endpoint> -U postgres -d payflow
```

---

## Part 21: AWS Deployment (Optional)

**File:** [implementation/part-21-aws-deployment.md](./implementation/part-21-aws-deployment.md)

### Tasks

- [ ] Create ECR repositories
- [ ] Push Docker images to ECR
- [ ] Create ECS cluster
- [ ] Create task definitions
- [ ] Create ECS services
- [ ] Configure ALB for load balancing
- [ ] Configure Route 53 (optional)
- [ ] Verify deployment

### Verification

```powershell
# Test deployed services
curl http://<alb-dns>/actuator/health
```

---

## Part 22: E2E Testing

**File:** [implementation/part-22-e2e-testing.md](./implementation/part-22-e2e-testing.md)

### Tasks

- [ ] Create e2e test folder
- [ ] Write REST Assured tests for:
  - [ ] User registration flow
  - [ ] User login flow
  - [ ] Merchant creation flow
- [ ] Write Playwright tests for:
  - [ ] Login page
  - [ ] Dashboard page
- [ ] Document test results

### Verification

```powershell
# Run E2E tests
cd e2e-tests
mvn test
```

---

## Part 23: Git & PR

**File:** [implementation/part-23-git-pr.md](./implementation/part-23-git-pr.md)

### Tasks

- [ ] Review all uncommitted changes
- [ ] Create meaningful commits
- [ ] Push to feature branch
- [ ] Create Pull Request
- [ ] Document changes in PR description
- [ ] Request code review (if applicable)

### Verification

```powershell
git status
git log --oneline -5
# Verify clean working tree
```

---

## Part 24: Sprint Summary

**File:** [implementation/part-24-sprint-summary.md](./implementation/part-24-sprint-summary.md)

### Tasks

- [ ] Review all deliverables
- [ ] Verify all services running
- [ ] Verify all APIs working
- [ ] Verify frontend working
- [ ] Document any known issues
- [ ] Plan for Sprint 2

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
- [ ] GET /v1/merchants/{id} returns merchant

### Frontend Working
- [x] Registration form submits successfully
- [x] Merchant onboarding form submits successfully
- [x] Login redirects to dashboard
- [x] JWT stored in localStorage (key: payflow_token)
- [ ] Protected routes redirect to login (optional)
- [x] Dashboard shows merchant info

### Docker & CI/CD
- [ ] All Dockerfiles build successfully
- [ ] docker compose up starts all services
- [ ] GitHub Actions CI passes

---

## Time Estimate

| Section | Parts | Estimated Time |
|---------|-------|----------------|
| Infrastructure & Gateway | 01-03 | 4-7 hours |
| Identity Service | 04-09 | 7-11 hours |
| Merchant Service | 10-13 | 4-7 hours |
| Frontend Dashboard | 14-17 | 5-8 hours |
| DevOps & Deployment | 18-21 | 4-8 hours |
| Testing & Documentation | 22-24 | 2-4 hours |
| **Total** | **24 parts** | **26-45 hours** |

---

## Implementation Order

```
Start
  │
  ▼
Part 01-03: Infrastructure & Gateway
  │ (Service discovery, config, routing ready)
  ▼
Part 04-09: Identity Service
  │ (Authentication ready)
  ▼
Part 10-13: Merchant Service
  │ (Merchant onboarding ready)
  ▼
Part 14-17: Frontend Dashboard
  │ (User interface ready)
  ▼
Part 18-21: DevOps & Deployment
  │ (Containerized, CI/CD ready)
  ▼
Part 22-24: Testing & Documentation
  │ (Verified, documented)
  ▼
Sprint 1 Complete! 🎉
  │
  ▼
Continue to Sprint 2: API Key Management
```

---

## Verified Configuration Values

| Item | Value |
|------|-------|
| Spring Boot | `3.2.5` |
| Spring Cloud | `2023.0.x` |
| Java | `17` |
| Service Registry Port | `8761` |
| Config Server Port | `8888` |
| API Gateway Port | `8080` |
| Identity Service Port | `8081` |
| Merchant Service Port | `8082` |
| Frontend Port | `3000` |
| PostgreSQL Port | `5432` |
| Redis Port | `6379` |
| Entity Folder | `model` (not entity) |
| ID Type | `String` / `VARCHAR(50)` |
| Timestamp Type | `Instant` |
| Token Storage Key | `payflow_token` |

---

**Start Implementation:** [implementation/part-01-service-registry.md](./implementation/part-01-service-registry.md)
