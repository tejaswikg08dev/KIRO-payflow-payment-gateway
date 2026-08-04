# Sprint 0, Part 02: Common Library Setup

**Duration:** 2-3 hours  
**Prerequisites:** Part 01 (Maven setup) completed

---

## 1. What We're Building

In this part, you'll create the **common-lib** module with shared code used by all microservices:
- **DTOs** — Standard API response format (ApiResponse, ErrorDetail, PagedResponse)
- **Constants** — Payment statuses and methods (PaymentStatus, PaymentMethod)
- **Exceptions** — Custom exceptions with global handler
- **Utilities** — ID generators (Stripe-like prefixed IDs)

---

## 2. Concepts Deep Dive

### Why a Common Library?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Common Library Benefits                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   WITHOUT common-lib:                WITH common-lib:                       │
│                                                                              │
│   identity-service/                  common-lib/ (ONE place)                │
│     └── ApiResponse.java             └── ApiResponse.java                   │
│   payment-service/                                                          │
│     └── ApiResponse.java (copy)      All services:                          │
│   merchant-service/                  <dependency>                           │
│     └── ApiResponse.java (copy)        <artifactId>common-lib</artifactId>  │
│                                      </dependency>                          │
│   Problems:                                                                  │
│   • Duplicate code everywhere        Benefits:                              │
│   • Changes needed in 11 places      • Change once, all services update    │
│   • Inconsistent formats             • Consistent API responses             │
│   • Bug fixes repeated               • Shared exception handling            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### What Goes in common-lib?

| Category | Classes | Purpose |
|----------|---------|---------|
| **DTOs** | ApiResponse, ErrorDetail, PagedResponse | Standard response format |
| **Constants** | PaymentStatus, PaymentMethod | Shared enums |
| **Exceptions** | PayflowException, ResourceNotFoundException, DuplicateResourceException, InvalidStateTransitionException, GlobalExceptionHandler | Consistent error handling |
| **Utilities** | IdGenerator | Generate unique IDs (Stripe-like: pay_xxx, ord_xxx) |

---

## 3. Prerequisites

- Part 01 completed (Maven project structure exists)
- common-lib folder structure created

---

## 4. Step-by-Step Implementation

### Step 4.1: Create ApiResponse.java

Create `common-lib/src/main/java/com/payflow/common/dto/ApiResponse.java`:

```java
package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response wrapper used by ALL services.
 * 
 * Every REST endpoint returns data wrapped in this format:
 * 
 * Success:
 * {
 *   "success": true,
 *   "data": { ... },
 *   "timestamp": "2026-07-19T14:30:00Z"
 * }
 * 
 * Error:
 * {
 *   "success": false,
 *   "error": { "code": "...", "message": "..." },
 *   "timestamp": "2026-07-19T14:30:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields in JSON
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;
    private Instant timestamp;

    /**
     * Create a success response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a success response without data (e.g., delete operations).
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ErrorDetail(code, message, null))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response with details.
     */
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ErrorDetail(code, message, details))
                .timestamp(Instant.now())
                .build();
    }
}
```

**Line-by-Line Explanation:**

| Annotation/Code | What It Does |
|-----------------|--------------|
| `@Data` | Lombok generates getters, setters, equals, hashCode, toString |
| `@Builder` | Lombok generates builder pattern |
| `@JsonInclude(NON_NULL)` | Don't include null fields in JSON output |
| `Instant timestamp` | When the response was generated |
| `static <T>` | Generic method that works with any type |
| `new ErrorDetail(...)` | Create error detail inline in factory method |


### Step 4.2: Create ErrorDetail.java

Create `common-lib/src/main/java/com/payflow/common/dto/ErrorDetail.java`:

```java
package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error detail returned in API error responses.
 * 
 * Example JSON:
 * {
 *   "code": "PAYMENT_DECLINED",
 *   "message": "Payment was declined due to insufficient funds",
 *   "details": { "bank_response_code": "51" }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    /** Machine-readable error code (e.g., "PAYMENT_DECLINED", "INVALID_API_KEY") */
    private String code;

    /** Human-readable error message */
    private String message;

    /** Additional context (optional) — can be any object */
    private Object details;
}
```

**Why `Object details` instead of `List<String>`?**

Using `Object` is more flexible:
- Can be a Map for field errors: `{ "email": "Invalid format", "amount": "Must be positive" }`
- Can be a String for simple details
- Can be any object that needs to be serialized


### Step 4.3: Create PagedResponse.java

Create `common-lib/src/main/java/com/payflow/common/dto/PagedResponse.java`:

```java
package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard paginated response for list endpoints.
 * 
 * Example:
 * GET /v1/payments?page=1&per_page=20
 * 
 * Response:
 * {
 *   "success": true,
 *   "data": [ {...}, {...} ],
 *   "pagination": { "total": 150, "page": 1, "per_page": 20, "total_pages": 8 },
 *   "timestamp": "2026-07-19T14:30:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    private boolean success;
    private List<T> data;
    private PaginationInfo pagination;
    private Instant timestamp;

    public static <T> PagedResponse<T> of(List<T> data, long total, int page, int perPage) {
        int totalPages = (int) Math.ceil((double) total / perPage);
        return PagedResponse.<T>builder()
                .success(true)
                .data(data)
                .pagination(new PaginationInfo(total, page, perPage, totalPages))
                .timestamp(Instant.now())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {
        private long total;
        private int page;
        private int perPage;
        private int totalPages;
    }
}
```

**Usage Example:**

```java
// In a controller
List<PaymentDto> payments = paymentService.findAll(page, perPage);
long totalCount = paymentService.count();
return PagedResponse.of(payments, totalCount, page, perPage);
```

---

### Step 4.4: Create PaymentStatus.java

Create `common-lib/src/main/java/com/payflow/common/constant/PaymentStatus.java`:

```java
package com.payflow.common.constant;

/**
 * All possible payment states in the PayFlow system.
 * 
 * State machine transitions:
 * CREATED → PROCESSING → AUTHORIZED → CAPTURED → SETTLED
 *                       → FAILED
 *            AUTHORIZED → VOIDED
 *            AUTHORIZED → EXPIRED
 *            CAPTURED   → REFUNDED (full)
 * CREATED → EXPIRED (30 min timeout)
 */
public enum PaymentStatus {

    /** Order created, waiting for customer to submit payment details */
    CREATED,

    /** Payment submitted, talking to bank (customer is waiting) */
    PROCESSING,

    /** Bank approved, money is HELD on customer's card (not yet deducted) */
    AUTHORIZED,

    /** Merchant confirmed, money is DEDUCTED from customer */
    CAPTURED,

    /** Money transferred to merchant's bank account (end of day batch) */
    SETTLED,

    /** Merchant cancelled before capture (hold released) */
    VOIDED,

    /** Money returned to customer after capture */
    REFUNDED,

    /** Bank declined or error occurred */
    FAILED,

    /** Customer didn't complete in time (30 min for order, 7 days for auth) */
    EXPIRED
}
```

**State Transition Diagram:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Payment State Machine                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   ┌─────────┐                                                                │
│   │ CREATED │──────────────────────────────────────────► EXPIRED             │
│   └────┬────┘     (30 min timeout, no payment attempt)                      │
│        │                                                                     │
│        │ customer submits card                                               │
│        ▼                                                                     │
│   ┌────────────┐                                                             │
│   │ PROCESSING │────────────────────────────────────────► FAILED            │
│   └─────┬──────┘    (bank declined, network error)                          │
│         │                                                                    │
│         │ bank approves                                                      │
│         ▼                                                                    │
│   ┌────────────┐                                                             │
│   │ AUTHORIZED │────► VOIDED (merchant cancels)                             │
│   │            │────► EXPIRED (7 days, merchant didn't capture)             │
│   └─────┬──────┘                                                             │
│         │                                                                    │
│         │ merchant captures                                                  │
│         ▼                                                                    │
│   ┌──────────┐                                                               │
│   │ CAPTURED │─────► REFUNDED (customer requests refund)                    │
│   └────┬─────┘                                                               │
│        │                                                                     │
│        │ end-of-day settlement batch                                         │
│        ▼                                                                     │
│   ┌─────────┐                                                                │
│   │ SETTLED │  (terminal state - money in merchant's bank)                  │
│   └─────────┘                                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Step 4.5: Create PaymentMethod.java

Create `common-lib/src/main/java/com/payflow/common/constant/PaymentMethod.java`:

```java
package com.payflow.common.constant;

/**
 * Payment methods supported by PayFlow.
 */
public enum PaymentMethod {

    /** Credit or Debit card (Visa, Mastercard, RuPay) */
    CARD,

    /** UPI (Unified Payments Interface) — India's instant payment */
    UPI,

    /** Net Banking (redirect to bank website) */
    NETBANKING,

    /** Internal wallet balance */
    WALLET
}
```

---

### Step 4.6: Create PayflowException.java

Create `common-lib/src/main/java/com/payflow/common/exception/PayflowException.java`:

```java
package com.payflow.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all PayFlow business errors.
 * All custom exceptions extend this.
 * 
 * Contains:
 * - errorCode: Machine-readable code (e.g., "PAYMENT_DECLINED")
 * - message: Human-readable description
 * - httpStatus: Which HTTP status to return (400, 404, 422, etc.)
 */
@Getter
public class PayflowException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public PayflowException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public PayflowException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
```


### Step 4.7: Create ResourceNotFoundException.java

Create `common-lib/src/main/java/com/payflow/common/exception/ResourceNotFoundException.java`:

```java
package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource is not found.
 * Returns HTTP 404.
 * 
 * Example usage:
 *   throw new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment with ID pay_xyz not found");
 *   
 * Or with field-based constructor:
 *   throw new ResourceNotFoundException("Payment", "id", "pay_xyz");
 *   // Results in: "Payment with id 'pay_xyz' not found"
 */
public class ResourceNotFoundException extends PayflowException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(
                resourceName.toUpperCase() + "_NOT_FOUND",
                String.format("%s with %s '%s' not found", resourceName, fieldName, fieldValue),
                HttpStatus.NOT_FOUND
        );
    }
}
```


### Step 4.8: Create DuplicateResourceException.java

Create `common-lib/src/main/java/com/payflow/common/exception/DuplicateResourceException.java`:

```java
package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when trying to create a resource that already exists.
 * Returns HTTP 409 Conflict.
 * 
 * Example: Registering with an email that's already taken.
 */
public class DuplicateResourceException extends PayflowException {

    public DuplicateResourceException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
```


### Step 4.9: Create InvalidStateTransitionException.java

Create `common-lib/src/main/java/com/payflow/common/exception/InvalidStateTransitionException.java`:

```java
package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting an invalid state transition on a payment.
 * Returns HTTP 400 Bad Request.
 * 
 * Example: Trying to capture a payment that is already VOIDED.
 *   "Cannot capture. Current status: 'VOIDED'. This action is not allowed in this state."
 */
public class InvalidStateTransitionException extends PayflowException {

    public InvalidStateTransitionException(String currentState, String attemptedAction) {
        super(
                "INVALID_STATE_TRANSITION",
                String.format("Cannot %s. Current status: '%s'. This action is not allowed in this state.",
                        attemptedAction, currentState),
                HttpStatus.BAD_REQUEST
        );
    }
}
```


### Step 4.10: Create GlobalExceptionHandler.java

Create `common-lib/src/main/java/com/payflow/common/exception/GlobalExceptionHandler.java`:

```java
package com.payflow.common.exception;

import com.payflow.common.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for ALL services.
 * 
 * Catches exceptions and converts them to standardized error responses.
 * Each service includes this via common-lib dependency.
 * 
 * Every error returns:
 * {
 *   "success": false,
 *   "error": { "code": "...", "message": "...", "details": {...} },
 *   "timestamp": "..."
 * }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle our custom business exceptions (PayflowException and subclasses).
     */
    @ExceptionHandler(PayflowException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayflowException(PayflowException ex) {
        log.warn("Business error: [{}] {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /**
     * Handle validation errors (@Valid on request body).
     * Example: @NotNull field is null, @Size exceeded, @Email invalid format.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation error: {}", fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", "Request validation failed", fieldErrors));
    }

    /**
     * Handle constraint violations (e.g., @Size on path variable).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", ex.getMessage()));
    }

    /**
     * Handle missing required headers (e.g., Idempotency-Key not provided).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing header: {}", ex.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("MISSING_HEADER",
                        String.format("Required header '%s' is missing", ex.getHeaderName())));
    }

    /**
     * Catch-all for unexpected errors (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
    }
}
```

**Exception Handler Flow:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Exception → Response Flow                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Exception Thrown                      Handler                 HTTP Status │
│   ──────────────────                    ───────                 ─────────── │
│   ResourceNotFoundException       →  handlePayflowException  →     404      │
│   DuplicateResourceException      →  handlePayflowException  →     409      │
│   InvalidStateTransitionException →  handlePayflowException  →     400      │
│   MethodArgumentNotValidException →  handleValidationException → 400      │
│   ConstraintViolationException    →  handleConstraintViolation → 400      │
│   MissingRequestHeaderException   →  handleMissingHeader     →     400      │
│   Any other Exception             →  handleGenericException  →     500      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Step 4.11: Create IdGenerator.java

Create `common-lib/src/main/java/com/payflow/common/util/IdGenerator.java`:

```java
package com.payflow.common.util;

import java.security.SecureRandom;

/**
 * Generates short, URL-friendly, unique IDs for all PayFlow entities.
 * 
 * Format: {prefix}_{10-character-alphanumeric}
 * 
 * Examples:
 *   pay_Hk7mN3xQp2   (payment)
 *   ord_LkR3d9xF2m   (order)
 *   rfnd_Qm4nP8wXv3  (refund)
 *   merch_xyz789abc   (merchant)
 *   key_a1b2c3d4e5    (API key)
 *   evt_f6g7h8i9j0    (event)
 *   stl_Mn2kP9wQr5    (settlement)
 * 
 * Why not UUID?
 * - UUIDs are 36 characters (with dashes) — too long for URLs and display
 * - Our IDs are 14-16 characters — short, readable, still unique enough
 * - Prefix tells you the entity type at a glance
 * 
 * Collision probability:
 * - 10 chars from 62-char alphabet = 62^10 = 839 trillion possibilities
 * - Practically zero collision risk for our scale
 */
public class IdGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ID_LENGTH = 10;

    /**
     * Generate a random ID with given prefix.
     * Example: generateId("pay") → "pay_Hk7mN3xQp2"
     */
    public static String generateId(String prefix) {
        StringBuilder sb = new StringBuilder(prefix.length() + 1 + ID_LENGTH);
        sb.append(prefix).append('_');
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Generate payment ID: pay_xxxxxxxxxx */
    public static String paymentId() {
        return generateId("pay");
    }

    /** Generate order ID: ord_xxxxxxxxxx */
    public static String orderId() {
        return generateId("ord");
    }

    /** Generate refund ID: rfnd_xxxxxxxxxx */
    public static String refundId() {
        return generateId("rfnd");
    }

    /** Generate merchant ID: merch_xxxxxxxxxx */
    public static String merchantId() {
        return generateId("merch");
    }

    /** Generate API key ID: key_xxxxxxxxxx */
    public static String apiKeyId() {
        return generateId("key");
    }

    /** Generate event ID: evt_xxxxxxxxxx */
    public static String eventId() {
        return generateId("evt");
    }

    /** Generate settlement ID: stl_xxxxxxxxxx */
    public static String settlementId() {
        return generateId("stl");
    }

    /** Generate user ID: usr_xxxxxxxxxx */
    public static String userId() {
        return generateId("usr");
    }
}
```

**Why SecureRandom instead of UUID?**

| Approach | Length | Example | Readability |
|----------|--------|---------|-------------|
| UUID | 36 chars | `550e8400-e29b-41d4-a716-446655440000` | Hard to read |
| Our IDs | 14-16 chars | `pay_Hk7mN3xQp2` | Easy to read, type-prefixed |

---

## 5. Verification

### Build common-lib

```powershell
cd common-lib
mvn clean install
```

**Expected Output:**
```
[INFO] BUILD SUCCESS
```

### Check compiled classes

```powershell
dir target\classes\com\payflow\common
```

**Expected:**
```
    Directory: common-lib\target\classes\com\payflow\common

Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         8/4/2026   3:00 PM                constant
d-----         8/4/2026   3:00 PM                dto
d-----         8/4/2026   3:00 PM                exception
d-----         8/4/2026   3:00 PM                util
```

---

## 6. File Structure After This Part

```
common-lib/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── payflow/
                    └── common/
                        ├── dto/
                        │   ├── ApiResponse.java       # Standard response wrapper
                        │   ├── ErrorDetail.java       # Error details
                        │   └── PagedResponse.java     # Paginated list response
                        ├── constant/
                        │   ├── PaymentStatus.java     # CREATED, AUTHORIZED, etc.
                        │   └── PaymentMethod.java     # CARD, UPI, NETBANKING, WALLET
                        ├── exception/
                        │   ├── PayflowException.java             # Base exception
                        │   ├── ResourceNotFoundException.java    # 404 errors
                        │   ├── DuplicateResourceException.java   # 409 errors
                        │   ├── InvalidStateTransitionException.java # Invalid state
                        │   └── GlobalExceptionHandler.java       # Converts to API response
                        └── util/
                            └── IdGenerator.java        # pay_xxx, ord_xxx IDs
```

---

## 7. Key Takeaways

| Class | Purpose | HTTP Status |
|-------|---------|-------------|
| `ApiResponse<T>` | Standard wrapper for all API responses | - |
| `ErrorDetail` | Structured error information with optional details | - |
| `PagedResponse<T>` | Wrapper for paginated list endpoints | - |
| `PaymentStatus` | All valid payment states (9 states) | - |
| `PayflowException` | Base custom exception | Varies |
| `ResourceNotFoundException` | Resource not found | 404 |
| `DuplicateResourceException` | Resource already exists | 409 |
| `InvalidStateTransitionException` | Invalid state change | 400 |
| `GlobalExceptionHandler` | Converts exceptions to API responses | - |
| `IdGenerator` | Creates Stripe-like prefixed IDs | - |

---

## 8. Q&A / Troubleshooting

### Q: Why use `Object` for ErrorDetail.details instead of `List<String>`?

**A:** More flexible. Can hold:
- Map for field validation errors
- String for simple messages
- Any POJO for complex contexts

### Q: Why SecureRandom instead of Random?

**A:** `SecureRandom` is cryptographically strong. For IDs that might be in URLs or used in security contexts, we want unpredictable values.

### Q: How do services use GlobalExceptionHandler?

**A:** Add component scan in main class:
```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.common", "com.payflow.identity"})
public class IdentityServiceApplication { ... }
```

---

## 9. Related Concepts

| Topic | What to Learn Next | When Needed |
|-------|-------------------|-------------|
| @Valid annotation | Bean validation in controllers | Sprint 1 |
| @ControllerAdvice | How exception handlers work | Sprint 1 |
| State Machine | Payment state transitions | Sprint 3 |
| Idempotency | Using IdGenerator for request dedup | Sprint 3 |

---

## 10. Next Steps

**Continue to:** [part-03-docker-infrastructure.md](./part-03-docker-infrastructure.md)

In the next part, you'll set up Docker containers for PostgreSQL, Redis, DynamoDB, and LocalStack (AWS services).
