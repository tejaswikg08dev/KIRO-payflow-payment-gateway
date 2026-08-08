# Sprint 1: Auth & Onboarding — Technical Design

**Sprint Duration:** 2 weeks  
**Goal:** Build authentication and merchant onboarding services

---

## 1. Architecture Overview

### 1.1 Services to Build

| Service | Port | Purpose |
|---------|------|---------|
| service-registry | 8761 | Service discovery (Eureka) |
| config-server | 8888 | Centralized configuration |
| api-gateway | 8080 | API routing, JWT validation |
| identity-service | 8081 | Authentication, user management |
| merchant-service | 8082 | Merchant onboarding |

### 1.2 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SPRINT 1 ARCHITECTURE                           │
│                                                                              │
│  ┌─────────────┐                                                            │
│  │   Client    │ ◄─────────── React Frontend (merchant-portal)              │
│  │  (Browser)  │                                                            │
│  └──────┬──────┘                                                            │
│         │ HTTP                                                               │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        API Gateway (:8080)                           │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐              │   │
│  │  │ JWT Filter  │  │ Rate Limiter │  │ Route Handler  │              │   │
│  │  └─────────────┘  └──────────────┘  └────────────────┘              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────┐              ┌─────────────┐                               │
│  │  Identity   │◄────────────►│  Merchant   │                               │
│  │  Service    │              │  Service    │                               │
│  │  (:8081)    │              │  (:8082)    │                               │
│  └──────┬──────┘              └──────┬──────┘                               │
│         │                            │                                       │
│         ▼                            ▼                                       │
│  ┌─────────────────────────────────────────┐                                │
│  │           PostgreSQL (:5432)            │                                │
│  │  ┌─────────────┐  ┌─────────────────┐   │                                │
│  │  │  identity   │  │    merchant     │   │                                │
│  │  │   schema    │  │     schema      │   │                                │
│  │  └─────────────┘  └─────────────────┘   │                                │
│  └─────────────────────────────────────────┘                                │
│                                                                              │
│  ┌───────────────────┐  ┌────────────────────┐                              │
│  │ Service Registry  │  │   Config Server    │                              │
│  │     (:8761)       │  │      (:8888)       │                              │
│  └───────────────────┘  └────────────────────┘                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 2. Service Design

### 2.1 Service Registry (Eureka Server)

**Purpose:** Service discovery - allows services to find each other without hardcoded URLs

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     HOW SERVICE DISCOVERY WORKS                              │
│                                                                              │
│  1. Services register themselves:                                            │
│     ┌───────────────┐                    ┌─────────────────┐                │
│     │ identity-     │ ──── Register ───► │    Eureka       │                │
│     │ service       │     "I'm at 8081"  │    Server       │                │
│     └───────────────┘                    │                 │                │
│     ┌───────────────┐                    │  Registry:      │                │
│     │ merchant-     │ ──── Register ───► │  • identity:8081│                │
│     │ service       │     "I'm at 8082"  │  • merchant:8082│                │
│     └───────────────┘                    └─────────────────┘                │
│                                                                              │
│  2. Gateway discovers services:                                              │
│     ┌───────────────┐                    ┌─────────────────┐                │
│     │ API Gateway   │ ──── Query ──────► │    Eureka       │                │
│     │               │ "Where is          │    Server       │                │
│     │               │  identity-service?"│                 │                │
│     │               │ ◄── Response ───── │  "At port 8081" │                │
│     └───────────────┘                    └─────────────────┘                │
│                                                                              │
│  Why this matters:                                                           │
│  - No hardcoded URLs in config                                               │
│  - Services can scale (multiple instances)                                   │
│  - Automatic failover                                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Config Server

**Purpose:** Centralized configuration management

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CENTRALIZED CONFIGURATION                                │
│                                                                              │
│  Without Config Server:              With Config Server:                     │
│  ┌─────────────────────┐            ┌─────────────────────┐                 │
│  │ identity-service    │            │ config-server       │                 │
│  │ application.yml     │            │ ├── application.yml │  (shared)       │
│  │ db.url=localhost    │            │ ├── identity.yml    │                 │
│  │ jwt.secret=xxx      │            │ ├── merchant.yml    │                 │
│  └─────────────────────┘            │ └── gateway.yml     │                 │
│  ┌─────────────────────┐            └──────────┬──────────┘                 │
│  │ merchant-service    │                       │                            │
│  │ application.yml     │                       │                            │
│  │ db.url=localhost    │  ◄─ Config fetched at startup                      │
│  │ (duplicated!)       │                       ▼                            │
│  └─────────────────────┘            ┌─────────────────────┐                 │
│                                     │ All services get    │                 │
│  Problem: Change config             │ config from ONE     │                 │
│  = Update every service             │ central place       │                 │
│                                     └─────────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 2.3 API Gateway

**Purpose:** Single entry point for all API requests

**Responsibilities:**
1. Route requests to correct service
2. Validate JWT tokens
3. Rate limiting
4. Request/response logging

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY REQUEST FLOW                              │
│                                                                              │
│  Client Request: POST /v1/merchants                                          │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        API GATEWAY                                   │   │
│  │                                                                      │   │
│  │  Step 1: Rate Limit Check                                           │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │ Check: Is this IP over limit?                               │   │   │
│  │  │ • If yes → Return 429 Too Many Requests                     │   │   │
│  │  │ • If no → Continue                                          │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                           │                                         │   │
│  │                           ▼                                         │   │
│  │  Step 2: JWT Validation                                             │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │ Check: Is JWT valid?                                        │   │   │
│  │  │ • Verify signature (using public key)                       │   │   │
│  │  │ • Check expiration                                          │   │   │
│  │  │ • If invalid → Return 401 Unauthorized                      │   │   │
│  │  │ • If valid → Extract user info, continue                    │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                           │                                         │   │
│  │                           ▼                                         │   │
│  │  Step 3: Route to Service                                           │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │ /v1/auth/**   → identity-service                            │   │   │
│  │  │ /v1/merchants → merchant-service                            │   │   │
│  │  │ /v1/payments  → payment-service                             │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                           │                                                  │
│                           ▼                                                  │
│                    merchant-service                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.4 Identity Service

**Purpose:** User authentication and management

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     IDENTITY SERVICE COMPONENTS                              │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                         identity-service                               │ │
│  │                                                                        │ │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐        │ │
│  │  │  Controller  │───►│   Service    │───►│   Repository     │        │ │
│  │  │  (REST API)  │    │  (Business   │    │  (Database       │        │ │
│  │  │              │    │   Logic)     │    │   Access)        │        │ │
│  │  └──────────────┘    └──────────────┘    └──────────────────┘        │ │
│  │                            │                      │                   │ │
│  │                            ▼                      ▼                   │ │
│  │                     ┌──────────────┐      ┌─────────────┐            │ │
│  │                     │ JWT Utility  │      │  PostgreSQL │            │ │
│  │                     │ (generate,   │      │  (identity  │            │ │
│  │                     │  validate)   │      │   schema)   │            │ │
│  │                     └──────────────┘      └─────────────┘            │ │
│  │                                                                        │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Key Classes:                                                                │
│  • AuthController      - REST endpoints (/v1/auth/*)                        │
│  • AuthService         - Registration, login logic                          │
│  • UserRepository      - JPA repository for users table                     │
│  • JwtTokenProvider    - Generate/validate JWT tokens                       │
│  • PasswordEncoder     - BCrypt password hashing                            │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 3. Authentication Design

### 3.1 JWT Token Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JWT AUTHENTICATION FLOW                            │
│                                                                              │
│  STEP 1: User logs in                                                        │
│  ┌────────┐          ┌──────────────┐          ┌─────────────┐              │
│  │ Client │──POST───►│  API Gateway │─────────►│  Identity   │              │
│  │        │ /login   │              │          │  Service    │              │
│  │        │ {email,  │ (no JWT      │          │             │              │
│  │        │  pass}   │  check for   │          │ 1. Find user│              │
│  │        │          │  /auth/*)    │          │ 2. Check pwd│              │
│  │        │          │              │          │ 3. Generate │              │
│  │        │          │              │          │    JWT      │              │
│  │        │◄─────────┼──────────────┼──────────┤             │              │
│  │        │ {token}  │              │          │             │              │
│  └────────┘          └──────────────┘          └─────────────┘              │
│                                                                              │
│  STEP 2: Client makes authenticated request                                  │
│  ┌────────┐          ┌──────────────┐          ┌─────────────┐              │
│  │ Client │──POST───►│  API Gateway │─────────►│  Merchant   │              │
│  │        │ /merchant│              │          │  Service    │              │
│  │        │ Header:  │ 1. Extract   │          │             │              │
│  │        │ Auth:    │    JWT       │ Header:  │ Process     │              │
│  │        │ Bearer   │ 2. Validate  │ X-User-Id│ request     │              │
│  │        │ {token}  │    signature │          │ using       │              │
│  │        │          │ 3. Add user  │          │ user ID     │              │
│  │        │          │    info to   │          │             │              │
│  │        │          │    headers   │          │             │              │
│  │        │◄─────────┼──────────────┼──────────┤             │              │
│  │        │ response │              │          │             │              │
│  └────────┘          └──────────────┘          └─────────────┘              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 JWT Token Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JWT TOKEN ANATOMY                                  │
│                                                                              │
│  Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0.signature │
│         │                    │                                │              │
│         │                    │                                │              │
│         ▼                    ▼                                ▼              │
│  ┌─────────────┐      ┌─────────────┐               ┌─────────────┐        │
│  │   HEADER    │      │   PAYLOAD   │               │  SIGNATURE  │        │
│  │ (metadata)  │      │   (claims)  │               │  (verify)   │        │
│  └─────────────┘      └─────────────┘               └─────────────┘        │
│                                                                              │
│  Header (Base64 decoded):                                                    │
│  {                                                                           │
│    "alg": "HS256",     ← Algorithm (HMAC + SHA-256)                         │
│    "typ": "JWT"        ← Token type                                         │
│  }                                                                           │
│                                                                              │
│  Payload (Base64 decoded):                                                   │
│  {                                                                           │
│    "sub": "550e8400-...",     ← Subject (user ID)                           │
│    "email": "user@example.com",                                             │
│    "role": "MERCHANT",                                                       │
│    "iat": 1722771600,         ← Issued at (Unix timestamp)                  │
│    "exp": 1722858000          ← Expires at (Unix timestamp)                 │
│  }                                                                           │
│                                                                              │
│  Signature:                                                                  │
│  HMACSHA256(                                                                 │
│    base64UrlEncode(header) + "." + base64UrlEncode(payload),               │
│    secretKey                                                                 │
│  )                                                                           │
│                                                                              │
│  Why HS256?                                                                  │
│  • Single service (identity-service) handles all auth                       │
│  • Simpler than RSA key pairs - just one secret key                         │
│  • Secret key stored securely in application config                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Password Security

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PASSWORD SECURITY WITH BCRYPT                        │
│                                                                              │
│  User enters: "SecurePass123"                                                │
│                    │                                                         │
│                    ▼                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  BCrypt Algorithm                                                    │   │
│  │                                                                      │   │
│  │  1. Generate random salt: "$2a$12$Rk8.E5.xQnZqN7xL"                 │   │
│  │                                                                      │   │
│  │  2. Combine password + salt                                          │   │
│  │                                                                      │   │
│  │  3. Hash 2^12 = 4096 times (cost factor 12)                         │   │
│  │     (This makes brute force very slow)                              │   │
│  │                                                                      │   │
│  │  4. Output: "$2a$12$Rk8.E5.xQnZqN7xL.hashed_password_here"          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                    │                                                         │
│                    ▼                                                         │
│  Stored in database: "$2a$12$Rk8.E5.xQnZqN7xLkY2pQe5HZ8..." (60 chars)     │
│                                                                              │
│  On login verification:                                                      │
│  • Extract salt from stored hash                                             │
│  • Hash entered password with same salt                                      │
│  • Compare hashes (not plain passwords!)                                     │
│                                                                              │
│  Why BCrypt?                                                                 │
│  ✓ Includes salt (prevents rainbow table attacks)                           │
│  ✓ Configurable cost (slow down brute force)                                │
│  ✓ Industry standard, battle-tested                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 4. Database Design

### 4.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ENTITY RELATIONSHIPS                                │
│                                                                              │
│  ┌─────────────────────────────────────┐                                    │
│  │             users                    │                                    │
│  │  (identity schema)                   │                                    │
│  ├─────────────────────────────────────┤                                    │
│  │ PK │ id         │ UUID              │                                    │
│  │    │ email      │ VARCHAR(255) UQ   │                                    │
│  │    │ password   │ VARCHAR(255)      │                                    │
│  │    │ full_name  │ VARCHAR(100)      │                                    │
│  │    │ role       │ VARCHAR(20)       │                                    │
│  │    │ status     │ VARCHAR(20)       │                                    │
│  │    │ created_at │ TIMESTAMP         │                                    │
│  │    │ updated_at │ TIMESTAMP         │                                    │
│  └───────────────┬─────────────────────┘                                    │
│                  │                                                           │
│                  │ 1:1 (one user can have one merchant)                      │
│                  │                                                           │
│                  ▼                                                           │
│  ┌─────────────────────────────────────┐                                    │
│  │           merchants                  │                                    │
│  │  (merchant schema)                   │                                    │
│  ├─────────────────────────────────────┤                                    │
│  │ PK │ id            │ VARCHAR(50)    │  ← Format: mer_xxxx                │
│  │ FK │ user_id       │ UUID UQ        │  ← Links to users.id              │
│  │    │ business_name │ VARCHAR(255)   │                                    │
│  │    │ business_type │ VARCHAR(50)    │  ← INDIVIDUAL, COMPANY             │
│  │    │ country       │ VARCHAR(2)     │  ← ISO code (IN, US)               │
│  │    │ status        │ VARCHAR(20)    │  ← PENDING, ACTIVE                 │
│  │    │ webhook_url   │ VARCHAR(500)   │                                    │
│  │    │ webhook_secret│ VARCHAR(255)   │                                    │
│  │    │ created_at    │ TIMESTAMP      │                                    │
│  │    │ updated_at    │ TIMESTAMP      │                                    │
│  └─────────────────────────────────────┘                                    │
│                                                                              │
│  Legend:                                                                     │
│  PK = Primary Key                                                            │
│  FK = Foreign Key                                                            │
│  UQ = Unique constraint                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 SQL Schema

```sql
-- Identity schema (for users)
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MERCHANT',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON identity.users(email);

-- Merchant schema
CREATE SCHEMA IF NOT EXISTS merchant;

CREATE TABLE merchant.merchants (
    id VARCHAR(50) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES identity.users(id),
    business_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    country VARCHAR(2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchants_user_id ON merchant.merchants(user_id);
```

---

## 5. Class Design

### 5.1 Identity Service Classes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    IDENTITY SERVICE CLASS STRUCTURE                          │
│                                                                              │
│  com.payflow.identity/                                                       │
│  │                                                                           │
│  ├── IdentityServiceApplication.java     ← Main class                       │
│  │                                                                           │
│  ├── controller/                                                             │
│  │   └── AuthController.java             ← REST endpoints                   │
│  │       • POST /v1/auth/register                                           │
│  │       • POST /v1/auth/login                                              │
│  │       • GET /v1/auth/me                                                  │
│  │                                                                           │
│  ├── service/                                                                │
│  │   ├── AuthService.java                ← Business logic                   │
│  │   │   • register(RegisterRequest)                                        │
│  │   │   • login(LoginRequest)                                              │
│  │   │   • getCurrentUser(userId)                                           │
│  │   └── impl/                                                               │
│  │       └── AuthServiceImpl.java                                           │
│  │                                                                           │
│  ├── repository/                                                             │
│  │   └── UserRepository.java             ← JPA repository                   │
│  │       • findByEmail(email)                                               │
│  │       • existsByEmail(email)                                             │
│  │                                                                           │
│  ├── model/                                                                  │
│  │   └── User.java                       ← JPA entity                       │
│  │       @Entity @Table(schema="identity")                                  │
│  │                                                                           │
│  ├── dto/                                                                    │
│  │   ├── RegisterRequest.java            ← Input DTOs                       │
│  │   ├── LoginRequest.java                                                  │
│  │   ├── AuthResponse.java               ← Output DTOs                      │
│  │   └── UserResponse.java                                                  │
│  │                                                                           │
│  ├── security/                                                               │
│  │   ├── JwtTokenProvider.java           ← JWT utility                      │
│  │   │   • generateToken(user)                                              │
│  │   │   • validateToken(token)                                             │
│  │   │   • getUserIdFromToken(token)                                        │
│  │   └── SecurityConfig.java             ← Spring Security config           │
│  │                                                                           │
│  └── config/                                                                 │
│      └── AppConfig.java                  ← Beans (PasswordEncoder)          │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 5.2 Merchant Service Classes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MERCHANT SERVICE CLASS STRUCTURE                          │
│                                                                              │
│  com.payflow.merchant/                                                       │
│  │                                                                           │
│  ├── MerchantServiceApplication.java     ← Main class                       │
│  │                                                                           │
│  ├── controller/                                                             │
│  │   └── MerchantController.java         ← REST endpoints                   │
│  │       • POST /v1/merchants                                               │
│  │       • GET /v1/merchants/me                                             │
│  │       • GET /v1/merchants/{id}                                           │
│  │                                                                           │
│  ├── service/                                                                │
│  │   ├── MerchantService.java            ← Business logic                   │
│  │   │   • createMerchant(request, userId)                                  │
│  │   │   • getMerchantByUserId(userId)                                      │
│  │   └── impl/                                                               │
│  │       └── MerchantServiceImpl.java                                       │
│  │                                                                           │
│  ├── repository/                                                             │
│  │   └── MerchantRepository.java         ← JPA repository                   │
│  │       • findByUserId(userId)                                             │
│  │       • existsByUserId(userId)                                           │
│  │                                                                           │
│  ├── model/                                                                  │
│  │   └── Merchant.java                   ← JPA entity                       │
│  │       @Entity @Table(schema="merchant")                                  │
│  │                                                                           │
│  └── dto/                                                                    │
│      ├── CreateMerchantRequest.java      ← Input DTO                        │
│      └── MerchantResponse.java           ← Output DTO                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. API Gateway Configuration

### 6.1 Route Configuration

```yaml
# api-gateway/src/main/resources/application.yml

spring:
  cloud:
    gateway:
      routes:
        # Auth routes (no JWT required)
        - id: auth-service
          uri: lb://IDENTITY-SERVICE
          predicates:
            - Path=/v1/auth/**
          filters:
            - StripPrefix=0
            
        # Merchant routes (JWT required)
        - id: merchant-service
          uri: lb://MERCHANT-SERVICE
          predicates:
            - Path=/v1/merchants/**
          filters:
            - JwtAuthenticationFilter
            - StripPrefix=0
```

### 6.2 JWT Filter Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        JWT FILTER IMPLEMENTATION                             │
│                                                                              │
│  Request comes in with header: Authorization: Bearer eyJhbGci...            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  JwtAuthenticationFilter                                             │   │
│  │                                                                      │   │
│  │  1. Extract token from header                                        │   │
│  │     String token = header.substring(7);  // Remove "Bearer "         │   │
│  │                                                                      │   │
│  │  2. Validate token                                                   │   │
│  │     • Check signature using PUBLIC key                               │   │
│  │     • Check expiration                                               │   │
│  │     if (!jwtUtil.isValid(token)) {                                   │   │
│  │         return 401 Unauthorized                                      │   │
│  │     }                                                                │   │
│  │                                                                      │   │
│  │  3. Extract user info                                                │   │
│  │     Claims claims = jwtUtil.getClaims(token);                        │   │
│  │     String userId = claims.getSubject();                             │   │
│  │     String role = claims.get("role");                                │   │
│  │                                                                      │   │
│  │  4. Add to request headers (for downstream services)                 │   │
│  │     request.headers.add("X-User-Id", userId);                        │   │
│  │     request.headers.add("X-User-Role", role);                        │   │
│  │                                                                      │   │
│  │  5. Continue to next filter / route                                  │   │
│  │     return chain.filter(exchange);                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  Downstream services:                                                        │
│  • Don't need to validate JWT again                                          │
│  • Just read X-User-Id header to know who's calling                         │
│  • Trust the gateway (internal network)                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Frontend Design

### 7.1 React Application Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MERCHANT PORTAL STRUCTURE                               │
│                                                                              │
│  merchant-portal/                                                            │
│  ├── package.json                                                            │
│  ├── vite.config.ts                                                          │
│  ├── tsconfig.json                                                           │
│  │                                                                           │
│  └── src/                                                                    │
│      ├── main.tsx                    ← Entry point                          │
│      ├── App.tsx                     ← Router setup                         │
│      │                                                                       │
│      ├── pages/                      ← Page components                      │
│      │   ├── Login.tsx               ← Login form                           │
│      │   ├── Register.tsx            ← Registration form                    │
│      │   ├── Dashboard.tsx           ← Main dashboard                       │
│      │   └── MerchantOnboarding.tsx  ← Merchant setup                       │
│      │                                                                       │
│      ├── components/                 ← Reusable components                  │
│      │   ├── Layout.tsx              ← Dashboard layout                     │
│      │   ├── Sidebar.tsx             ← Navigation                           │
│      │   ├── Input.tsx               ← Form input                           │
│      │   └── Button.tsx              ← Button styles                        │
│      │                                                                       │
│      ├── api/                        ← API client                           │
│      │   ├── client.ts               ← Axios instance                       │
│      │   ├── auth.ts                 ← Auth API calls                       │
│      │   └── merchant.ts             ← Merchant API calls                   │
│      │                                                                       │
│      ├── store/                      ← State management                     │
│      │   └── authStore.ts            ← Zustand auth store                   │
│      │                                                                       │
│      ├── hooks/                      ← Custom hooks                         │
│      │   └── useAuth.ts              ← Auth utilities                       │
│      │                                                                       │
│      └── types/                      ← TypeScript types                     │
│          └── index.ts                ← Shared types                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Authentication Flow (Frontend)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       FRONTEND AUTH FLOW                                     │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                           LOGIN FLOW                                   │ │
│  │                                                                        │ │
│  │  1. User enters email/password                                        │ │
│  │         │                                                              │ │
│  │         ▼                                                              │ │
│  │  2. Call: POST /v1/auth/login                                         │ │
│  │         │                                                              │ │
│  │         ▼                                                              │ │
│  │  3. Receive: { accessToken, user }                                    │ │
│  │         │                                                              │ │
│  │         ▼                                                              │ │
│  │  4. Store token: localStorage.setItem('token', accessToken)           │ │
│  │         │                                                              │ │
│  │         ▼                                                              │ │
│  │  5. Update state: authStore.setUser(user)                             │ │
│  │         │                                                              │ │
│  │         ▼                                                              │ │
│  │  6. Redirect to Dashboard                                              │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                    AUTHENTICATED REQUEST FLOW                          │ │
│  │                                                                        │ │
│  │  Axios interceptor automatically adds token:                          │ │
│  │                                                                        │ │
│  │  axios.interceptors.request.use((config) => {                         │ │
│  │    const token = localStorage.getItem('token');                       │ │
│  │    if (token) {                                                       │ │
│  │      config.headers.Authorization = `Bearer ${token}`;                │ │
│  │    }                                                                   │ │
│  │    return config;                                                      │ │
│  │  });                                                                   │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Docker Configuration

### 8.1 Service Dockerfiles

```dockerfile
# identity-service/Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/identity-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 8.2 Docker Compose (Full Stack)

```yaml
# docker-compose.yml (Sprint 1 services)
version: '3.8'

services:
  service-registry:
    build: ./service-registry
    ports:
      - "8761:8761"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      
  config-server:
    build: ./config-server
    ports:
      - "8888:8888"
    depends_on:
      - service-registry
      
  api-gateway:
    build: ./api-gateway
    ports:
      - "8080:8080"
    depends_on:
      - service-registry
      - config-server
      
  identity-service:
    build: ./identity-service
    ports:
      - "8081:8081"
    depends_on:
      - service-registry
      - config-server
      - postgres
      
  merchant-service:
    build: ./merchant-service
    ports:
      - "8082:8082"
    depends_on:
      - service-registry
      - config-server
      - postgres
```

---

## 9. Testing Strategy

### 9.1 Test Pyramid

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           TEST PYRAMID                                       │
│                                                                              │
│                              /\                                              │
│                             /  \         E2E Tests                           │
│                            /    \        (few, slow)                         │
│                           /  E2E \       - Full login flow                   │
│                          /────────\                                          │
│                         /          \                                         │
│                        / Integration \   Integration Tests                   │
│                       /              \   (medium)                            │
│                      /  Integration   \  - API endpoint tests                │
│                     /──────────────────\ - Repository tests                  │
│                    /                    \                                    │
│                   /     Unit Tests       \  Unit Tests                       │
│                  /                        \ (many, fast)                     │
│                 /        Unit Tests        \ - Service logic                 │
│                /────────────────────────────\ - JWT validation               │
│                                               - Password hashing             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Test Examples

| Test Type | Example | Tool |
|-----------|---------|------|
| Unit | `AuthServiceTest.testRegister()` | JUnit 5 |
| Unit | `JwtTokenProviderTest.testValidate()` | JUnit 5 |
| Integration | `AuthControllerIT.testLogin()` | Spring Test |
| Integration | `UserRepositoryIT.testFindByEmail()` | TestContainers |
| E2E | Login → Dashboard flow | Postman/Newman |

---

## 10. Sequence Diagrams

### 10.1 User Registration Sequence

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    USER REGISTRATION SEQUENCE                                │
│                                                                              │
│  Client          Gateway         Identity         DB                        │
│    │                │               │               │                        │
│    │ POST /register │               │               │                        │
│    │───────────────►│               │               │                        │
│    │                │ route         │               │                        │
│    │                │──────────────►│               │                        │
│    │                │               │ check email   │                        │
│    │                │               │──────────────►│                        │
│    │                │               │◄──────────────│                        │
│    │                │               │               │                        │
│    │                │               │ hash password │                        │
│    │                │               │ (BCrypt)      │                        │
│    │                │               │               │                        │
│    │                │               │ save user     │                        │
│    │                │               │──────────────►│                        │
│    │                │               │◄──────────────│                        │
│    │                │               │               │                        │
│    │                │               │ generate JWT  │                        │
│    │                │               │ (sign w/      │                        │
│    │                │               │  private key) │                        │
│    │                │               │               │                        │
│    │                │◄──────────────│               │                        │
│    │◄───────────────│ { token,user} │               │                        │
│    │                │               │               │                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Service Discovery | Eureka | Spring Cloud native, battle-tested |
| Config Management | Spring Cloud Config | Centralized, supports profiles |
| API Gateway | Spring Cloud Gateway | Reactive, filter-based |
| Auth | JWT + HS256 | Stateless, simple HMAC secret key |
| Password Hashing | BCrypt | Industry standard, configurable cost |
| Frontend | React + TypeScript | Type safety, large ecosystem |
| Build Tool | Vite | Fast HMR, modern tooling |

---

## 12. Next Steps

After completing the design review:

1. **Part 01:** Service Registry & Config Server
2. **Part 02:** API Gateway with JWT Filter
3. **Part 03:** Identity Service
4. **Part 04:** Merchant Service
5. **Part 05:** React Frontend (Login/Register)
6. **Part 06:** Docker & Testing

**Continue to:** [tasks.md](./tasks.md) - Implementation task list

---

**End of Sprint 1 Design Document**
