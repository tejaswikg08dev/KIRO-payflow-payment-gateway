# 📋 Documentation vs Source Code Discrepancy Report

**Generated:** August 4, 2026  
**Scope:** Sprint 00 Foundation Implementation Documentation

---

## Executive Summary

This report identifies discrepancies between the implementation documentation in `docs/03-sprints/` and the actual source code in the repository. Each item needs to be corrected in the documentation to match the actual working code.

---

## ❌ CRITICAL DISCREPANCIES

### 1. Parent pom.xml - Multiple Differences

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Spring Boot Version | `3.2.0` | `3.2.5` | ❌ MISMATCH |
| Spring Cloud Version | `2023.0.0` | `2023.0.1` | ❌ MISMATCH |
| Modules | Only `common-lib` | All 12 modules listed | ❌ MISMATCH |
| Properties | Missing `mapstruct.version`, `lombok.version`, `resilience4j.version` | Has `mapstruct.version=1.5.5.Final`, `lombok.version=1.18.32`, `resilience4j.version=2.2.0` | ❌ MISSING |
| DependencyManagement | Has JWT libraries (jjwt-api, jjwt-impl, jjwt-jackson) | Missing JWT libraries, has MapStruct, Resilience4j | ❌ MISMATCH |
| Compiler Plugin | Has specific annotation processor for Lombok only | Has annotation processors for Lombok + MapStruct | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-01-project-initialization.md`

---

### 2. common-lib/pom.xml - Minor Differences

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Description | "Shared DTOs, exceptions, and utilities" | "Shared DTOs, exceptions, utilities, and constants used across all services" | ⚠️ Minor |
| Build Plugin | Missing spring-boot-maven-plugin skip config | Has `<skip>true</skip>` for spring-boot-maven-plugin | ❌ MISSING |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-01-project-initialization.md`

---

### 3. Docker Compose Infrastructure

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Filename | `docker-compose.infra.yml` | `docker-compose-infra.yml` | ❌ MISMATCH |
| PostgreSQL Password | `payflow123` | `payflow_secret` | ❌ MISMATCH |
| PostgreSQL Image | `postgres:15-alpine` | `postgres:15` | ⚠️ Minor |
| DynamoDB | Inside LocalStack (port 8000) | Separate container `dynamodb-local` (port 8000) | ❌ MISMATCH |
| LocalStack Services | `sqs,sns,dynamodb` | `sqs,sns` (no dynamodb) | ❌ MISMATCH |
| LocalStack Region | Not specified | `DEFAULT_REGION: ap-south-1` | ❌ MISSING |
| Redis Command | `redis-server --appendonly yes` | `redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru` | ❌ MISMATCH |
| Redis Volume | Has `redis-data` volume | No Redis volume | ❌ MISMATCH |
| LocalStack Volume | Has `localstack-data` volume | No LocalStack data volume | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-03-docker-infrastructure.md`

---

### 4. init-db.sql

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Content | Verbose with SELECT confirmation | Simple, minimal | ⚠️ Style |
| Grant Syntax | `GRANT ALL ON SCHEMA` | `GRANT ALL PRIVILEGES ON SCHEMA` | ⚠️ Minor |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-03-docker-infrastructure.md`

---

### 5. init-localstack.sh

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Queue Names | `payment-events-queue`, `webhook-delivery-queue`, `notification-queue`, `payment-events-dlq` | `payflow-payment-events`, `payflow-webhook-delivery`, `payflow-notification`, `payflow-payment-events-dlq`, `payflow-webhook-delivery-dlq` | ❌ MISMATCH |
| Topic Names | `email-notifications`, `sms-notifications` | `payflow-email-notifications`, `payflow-sms-notifications` | ❌ MISMATCH |
| DynamoDB Tables | Creates `webhook_events` and `audit_trail` tables | NO DynamoDB commands (DynamoDB is separate container) | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-03-docker-infrastructure.md`

---

### 6. common-lib Java Classes

#### ApiResponse.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| timestamp field | `@Builder.Default` with `Instant.now()` | No default, timestamp set in factory methods | ❌ MISMATCH |
| error() method signature | `error(ErrorDetail errorDetail)` overload exists | No `error(ErrorDetail)` overload, has `error(String, String, Object)` | ❌ MISMATCH |
| ErrorDetail creation | `ErrorDetail.of(code, message)` | `new ErrorDetail(code, message, null)` | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

#### ErrorDetail.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| details field type | `List<String>` | `Object` | ❌ MISMATCH |
| Has @Builder | Yes | No | ❌ MISMATCH |
| Static factory methods | `of(code, message)`, `of(code, message, details)` | None (uses constructor) | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

#### PaymentStatus.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Has description field | Yes (with getDescription method) | No | ❌ MISMATCH |
| EXPIRED status | Not present | Present | ❌ MISSING |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

#### PaymentMethod.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Has displayName field | Yes (with getDisplayName method) | No | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

#### ResourceNotFoundException.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| Constructor | `(resourceType, resourceId)` | `(errorCode, message)` and `(resourceName, fieldName, fieldValue)` | ❌ MISMATCH |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

#### GlobalExceptionHandler.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| ConstraintViolationException handler | Not present | Present | ❌ MISSING |
| MissingRequestHeaderException handler | Not present | Present | ❌ MISSING |
| Import | Uses `FieldError` extraction | Uses `Map<String, String> fieldErrors` | ⚠️ Style |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

#### IdGenerator.java

| Field | Documentation | Actual Source Code | Status |
|-------|---------------|-------------------|--------|
| ID Length | 16 characters | 10 characters | ❌ MISMATCH |
| Algorithm | UUID-based | SecureRandom with custom alphabet | ❌ MISMATCH |
| Method names | `generateOrderId()`, `generatePaymentId()`, etc. | `orderId()`, `paymentId()`, etc. | ❌ MISMATCH |
| Additional methods | `generateIdempotencyKey()` | `userId()`, `apiKeyId()`, `eventId()` | ❌ MISSING/EXTRA |

**File:** `docs/03-sprints/sprint-00-foundation/implementation/part-02-common-lib-setup.md`

---

### 7. Missing Classes in Documentation

The actual source code has these classes that are NOT documented:

| Class | Location | Status |
|-------|----------|--------|
| `PagedResponse.java` | dto/ | ❌ NOT DOCUMENTED |
| `DuplicateResourceException.java` | exception/ | ❌ NOT DOCUMENTED |
| `InvalidStateTransitionException.java` | exception/ | ❌ NOT DOCUMENTED |

---

### 8. .gitignore Differences

| Item | Documentation | Actual Source Code | Status |
|------|---------------|-------------------|--------|
| Terraform patterns | Not present | Present (`*.tfstate`, `.terraform/`) | ❌ MISSING |
| AWS patterns | Not present | Present (`.aws/`, `*.pem`) | ❌ MISSING |
| Jacoco patterns | Not present | Present (`jacoco/`) | ❌ MISSING |
| cache patterns | Not present | Present (`.cache/`) | ❌ MISSING |

---

## Summary Statistics

```
┌─────────────────────────────────────────┬───────────────────┐
│ Category                                │ Count             │
├─────────────────────────────────────────┼───────────────────┤
│ Critical Mismatches (❌)                │ 45                │
│ Minor Differences (⚠️)                  │ 8                 │
│ Missing Documentation                   │ 3 classes         │
├─────────────────────────────────────────┼───────────────────┤
│ TOTAL ITEMS TO FIX                      │ 56                │
└─────────────────────────────────────────┴───────────────────┘
```

---

## Files Requiring Updates

1. **`part-01-project-initialization.md`** - Parent pom.xml code
2. **`part-02-common-lib-setup.md`** - All Java class code (6 classes + 3 new)
3. **`part-03-docker-infrastructure.md`** - docker-compose + init scripts
4. **`part-04-git-workflow.md`** - .gitignore patterns

---

## Recommended Fix Priority

### Priority 1: Critical (Must Fix)
1. Parent pom.xml versions and modules
2. Docker compose filename and configurations
3. All common-lib Java classes
4. Init scripts (queue/topic names)

### Priority 2: Important (Should Fix)
1. Add missing classes (PagedResponse, DuplicateResourceException, InvalidStateTransitionException)
2. .gitignore additional patterns

### Priority 3: Minor (Nice to Have)
1. Description text differences
2. Code style variations

---

**End of Discrepancy Report**
