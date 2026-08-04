# Sprint 1: Auth & Onboarding — Requirements

**Sprint Duration:** 2 weeks  
**Goal:** Users can register and login, merchants can onboard to the platform

---

## Executive Summary

Sprint 1 introduces the foundational authentication and merchant onboarding capabilities. By the end of this sprint, users will be able to register accounts, login with JWT tokens, and merchants can create their business profiles to start accepting payments.

---

## Functional Requirements

### FR-1: User Registration

**Description:** New users must be able to create an account with email and password.

**Acceptance Criteria:**
| ID | Criteria | Priority |
|----|----------|----------|
| FR-1.1 | User can register with email, password, full name | Must |
| FR-1.2 | Email must be unique across the system | Must |
| FR-1.3 | Password must meet complexity requirements (8+ chars, 1 uppercase, 1 number) | Must |
| FR-1.4 | System returns JWT access token upon successful registration | Must |
| FR-1.5 | System returns HTTP 409 if email already exists | Must |
| FR-1.6 | System returns HTTP 400 if validation fails | Must |

**API Endpoint:**
```
POST /v1/auth/register
```

---

### FR-2: User Login

**Description:** Registered users must be able to authenticate with their credentials.

**Acceptance Criteria:**
| ID | Criteria | Priority |
|----|----------|----------|
| FR-2.1 | User can login with email and password | Must |
| FR-2.2 | System returns JWT access token upon successful login | Must |
| FR-2.3 | JWT token expires after 24 hours | Must |
| FR-2.4 | System returns HTTP 401 for invalid credentials | Must |
| FR-2.5 | System returns HTTP 400 if email/password missing | Must |
| FR-2.6 | Failed login attempts are logged for security audit | Should |

**API Endpoint:**
```
POST /v1/auth/login
```

---

### FR-3: Merchant Registration

**Description:** Authenticated users can register their business as a merchant.

**Acceptance Criteria:**
| ID | Criteria | Priority |
|----|----------|----------|
| FR-3.1 | Merchant can register with business name, type, country | Must |
| FR-3.2 | Each user can only have one merchant account | Must |
| FR-3.3 | System generates unique merchant ID (format: `mer_xxxx`) | Must |
| FR-3.4 | Merchant starts in PENDING status | Must |
| FR-3.5 | System stores business address and contact information | Should |
| FR-3.6 | System returns HTTP 409 if user already has merchant | Must |

**API Endpoint:**
```
POST /v1/merchants
```

---

### FR-4: Get Current User

**Description:** Authenticated users can retrieve their profile information.

**Acceptance Criteria:**
| ID | Criteria | Priority |
|----|----------|----------|
| FR-4.1 | User can retrieve their profile with valid JWT | Must |
| FR-4.2 | Response includes id, email, name, role, createdAt | Must |
| FR-4.3 | System returns HTTP 401 if token is invalid/expired | Must |

**API Endpoint:**
```
GET /v1/auth/me
```

---

### FR-5: Get Merchant Profile

**Description:** Authenticated users can retrieve their merchant profile.

**Acceptance Criteria:**
| ID | Criteria | Priority |
|----|----------|----------|
| FR-5.1 | User can retrieve their merchant profile with valid JWT | Must |
| FR-5.2 | Response includes all merchant details | Must |
| FR-5.3 | System returns HTTP 404 if user has no merchant | Must |

**API Endpoint:**
```
GET /v1/merchants/me
```

---

## Non-Functional Requirements

### NFR-1: Security

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1.1 | Passwords must be hashed with BCrypt (cost factor 12) | Must |
| NFR-1.2 | JWT tokens must be signed with RS256 algorithm | Must |
| NFR-1.3 | API Gateway must validate JWT on all protected routes | Must |
| NFR-1.4 | No plain-text credentials in logs | Must |

### NFR-2: Performance

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-2.1 | Login API response time < 500ms (p95) | Should |
| NFR-2.2 | Registration API response time < 1000ms (p95) | Should |
| NFR-2.3 | System handles 100 concurrent login requests | Should |

### NFR-3: Availability

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-3.1 | Auth service uptime > 99.5% | Should |
| NFR-3.2 | Service auto-recovers from container restart | Must |

---

## Data Requirements

### Users Table

```
┌──────────────────────────────────────────────────────────────────┐
│                         users table                               │
├──────────────┬──────────────┬────────────────────────────────────┤
│ Column       │ Type         │ Description                        │
├──────────────┼──────────────┼────────────────────────────────────┤
│ id           │ UUID         │ Primary key                        │
│ email        │ VARCHAR(255) │ Unique, not null                   │
│ password     │ VARCHAR(255) │ BCrypt hash, not null              │
│ full_name    │ VARCHAR(100) │ Not null                           │
│ role         │ VARCHAR(20)  │ MERCHANT, ADMIN                    │
│ status       │ VARCHAR(20)  │ ACTIVE, INACTIVE, LOCKED           │
│ created_at   │ TIMESTAMP    │ Not null                           │
│ updated_at   │ TIMESTAMP    │ Not null                           │
└──────────────┴──────────────┴────────────────────────────────────┘
```

### Merchants Table

```
┌──────────────────────────────────────────────────────────────────┐
│                       merchants table                             │
├──────────────────┬──────────────┬────────────────────────────────┤
│ Column           │ Type         │ Description                    │
├──────────────────┼──────────────┼────────────────────────────────┤
│ id               │ VARCHAR(50)  │ Primary key (mer_xxxx)         │
│ user_id          │ UUID         │ Foreign key to users           │
│ business_name    │ VARCHAR(255) │ Not null                       │
│ business_type    │ VARCHAR(50)  │ INDIVIDUAL, COMPANY            │
│ country          │ VARCHAR(2)   │ ISO 3166-1 alpha-2             │
│ status           │ VARCHAR(20)  │ PENDING, ACTIVE, SUSPENDED     │
│ webhook_url      │ VARCHAR(500) │ Nullable                       │
│ webhook_secret   │ VARCHAR(255) │ Nullable                       │
│ created_at       │ TIMESTAMP    │ Not null                       │
│ updated_at       │ TIMESTAMP    │ Not null                       │
└──────────────────┴──────────────┴────────────────────────────────┘
```

---

## API Specifications

### Authentication APIs

#### POST /v1/auth/register

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "fullName": "John Doe"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "fullName": "John Doe",
      "role": "MERCHANT"
    }
  }
}
```

#### POST /v1/auth/login

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "user@example.com",
      "fullName": "John Doe",
      "role": "MERCHANT"
    }
  }
}
```

### Merchant APIs

#### POST /v1/merchants

**Request:**
```json
{
  "businessName": "Acme Store",
  "businessType": "COMPANY",
  "country": "IN"
}
```

**Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": "mer_abc123xyz",
    "businessName": "Acme Store",
    "businessType": "COMPANY",
    "country": "IN",
    "status": "PENDING",
    "createdAt": "2026-08-04T10:30:00Z"
  }
}
```

---

## User Stories

### US-1: New User Registration
**As a** new user  
**I want to** create an account with my email and password  
**So that** I can access the PayFlow platform

### US-2: User Login
**As a** registered user  
**I want to** login with my credentials  
**So that** I can access my dashboard

### US-3: Merchant Onboarding
**As a** logged-in user  
**I want to** register my business as a merchant  
**So that** I can start accepting payments

### US-4: View Profile
**As a** logged-in user  
**I want to** view my profile information  
**So that** I can verify my account details

---

## Glossary

| Term | Definition |
|------|------------|
| JWT | JSON Web Token - A compact, self-contained token for authentication |
| BCrypt | Password hashing algorithm with salt |
| RS256 | RSA Signature with SHA-256 (asymmetric algorithm) |
| Merchant | A business entity registered to accept payments |
| Access Token | Short-lived token for API authentication |

---

## Dependencies

### External Dependencies
- PostgreSQL (from Sprint 0)
- Redis (for future session management)

### Internal Dependencies
- common-lib module (DTOs, exceptions)
- Spring Security
- Spring Cloud Gateway

---

## Out of Scope for Sprint 1

- Password reset functionality
- Email verification
- Two-factor authentication
- Merchant approval workflow
- API key generation (Sprint 2)

---

## Success Metrics

| Metric | Target |
|--------|--------|
| All acceptance criteria met | 100% |
| Unit test coverage | > 80% |
| Integration tests passing | 100% |
| No critical security vulnerabilities | 0 |

---

**Next:** [design.md](./design.md) - Technical design for Sprint 1
