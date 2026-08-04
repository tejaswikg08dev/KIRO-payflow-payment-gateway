# Phase 3 — Part 1: Parent POM & Common Library

> This is where we START writing code. In this part, we create:
> 1. The parent pom.xml (controls versions for ALL services)
> 2. The common-lib module (shared code used by every service)
>
> After this part, you'll have a compilable Java project structure.

---

## 1. What Is a Parent POM? (Concept)

In a multi-module Maven project, the parent POM is the "root" that:

```
PARENT POM (payflow-payment-gateway/pom.xml):
├── Declares ALL child modules (services)
├── Sets Java version (17) in ONE place
├── Sets Spring Boot version in ONE place
├── Sets ALL dependency versions in ONE place
├── Configures shared plugins (compiler, Docker)
│
└── BENEFIT: If you need to update Spring Boot from 3.2.5 to 3.3.0:
    ├── Change ONE line in parent POM
    ├── ALL 12 services automatically use 3.3.0
    └── No need to edit 12 separate pom.xml files!
```

---

## 2. Create Parent POM

**File:** `payflow-payment-gateway/pom.xml`

This file already exists in the project root. Let me explain every section:

### 2.1 Parent Declaration

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
    <relativePath/>
</parent>
```

**What this means:**
- Our project inherits from Spring Boot's parent POM
- Spring Boot 3.2.5 manages 400+ dependency versions for us
- We don't need to specify versions for spring-boot-starter-web, jackson, etc.

### 2.2 Project Coordinates

```xml
<groupId>com.payflow</groupId>
<artifactId>payflow-payment-gateway</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

**Explanation:**
- `groupId`: Our organization/company (like a Java package prefix)
- `artifactId`: This project's unique name
- `version`: 1.0.0-SNAPSHOT (SNAPSHOT = still in development, not released)
- `packaging`: "pom" means this is a parent (not a JAR/WAR)

### 2.3 Modules Declaration

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>
    <module>api-gateway</module>
    <module>identity-service</module>
    <module>merchant-service</module>
    <module>payment-service</module>
    <module>routing-service</module>
    <module>settlement-service</module>
    <module>webhook-service</module>
    <module>notification-service</module>
    <module>bank-simulator</module>
</modules>
```

**What this means:**
- These are all the child folders that Maven will build
- Running `mvn clean install` from root builds ALL of them
- Running `mvn -pl identity-service clean install` builds just one

### 2.4 Properties (Version Numbers)

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <springdoc.version>2.3.0</springdoc.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <lombok.version>1.18.32</lombok.version>
    <resilience4j.version>2.2.0</resilience4j.version>
</properties>
```

**Why here?** Change version in ONE place → applies everywhere.

### 2.5 Dependency Management

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        ...
    </dependencies>
</dependencyManagement>
```

**What is dependencyManagement?**
- It does NOT add dependencies to any module
- It only CONTROLS versions
- When a child module says `<artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>` without a version, Maven looks here for the version

### 2.6 How to Build

```cmd
cd payflow-payment-gateway
mvn clean install -DskipTests
```

This will:
1. Compile all modules in dependency order
2. common-lib first (others depend on it)
3. Then all services
4. Package each as a JAR

---

## 3. What Is the Common Library? (Concept)

The common-lib is a shared Java library (JAR) that contains code
used by EVERY service. Instead of copying the same classes into
7 services, we write once and share:

```
common-lib provides:
├── dto/ApiResponse.java       → Standard JSON response format
├── dto/ErrorDetail.java       → Error response structure
├── dto/PagedResponse.java     → Paginated list response
├── constant/PaymentStatus.java → Payment state enum
├── constant/PaymentMethod.java → Payment method enum
├── exception/PayflowException.java → Base exception class
├── exception/ResourceNotFoundException.java → 404 errors
├── exception/DuplicateResourceException.java → 409 errors
├── exception/InvalidStateTransitionException.java → State machine errors
├── exception/GlobalExceptionHandler.java → Catches ALL exceptions
└── util/IdGenerator.java      → Generates pay_xxx, ord_xxx IDs
```

### 3.1 How Services Use common-lib

Each service adds this to its pom.xml:
```xml
<dependency>
    <groupId>com.payflow</groupId>
    <artifactId>common-lib</artifactId>
</dependency>
```

(No version needed — parent POM manages it!)

Then in code:
```java
import com.payflow.common.dto.ApiResponse;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
```

---

## 4. Common Library — Complete Annotated Source Code (File by File)

> **How to use this section:** Type each file line by line. Every annotation, method,
> and design decision is explained inline. By the end, you'll understand not just WHAT
> the code does, but WHY every line exists.

---

### 4.1 common-lib/pom.xml — The Library's Build File

**File:** `common-lib/pom.xml`

**Purpose:** Tells Maven how to build common-lib as a plain JAR (not a runnable application).
This is the FIRST file you create in the common-lib folder.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- XML declaration: tells parser this is XML version 1.0, encoded in UTF-8 -->

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <!-- These xmlns attributes define the XML schema. You type them once, never change them. -->

    <modelVersion>4.0.0</modelVersion>
    <!-- Always 4.0.0 — this is the POM schema version Maven uses -->

    <!-- WHO IS OUR PARENT? The root pom.xml in the project root. -->
    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <!--
        WHY: By declaring a parent, we inherit:
        - Java 17 compilation settings
        - All dependency version numbers (Spring Boot, Jackson, Lombok)
        - Shared build plugins
        Without this, we'd need to redeclare everything in every module.
    -->

    <artifactId>common-lib</artifactId>
    <!-- This module's unique name within the group. Maven uses this for the JAR filename. -->

    <name>PayFlow Common Library</name>
    <!-- Human-readable name shown in IDE project views and Maven output -->

    <description>Shared DTOs, exceptions, utilities, and constants used across all services</description>
    <!-- Documentation for anyone browsing the POM -->

    <!-- This module is a library (JAR), not a runnable app -->
    <packaging>jar</packaging>
    <!--
        WHY jar and not spring-boot:
        - "jar" = plain Java library, can be imported by other modules
        - Spring Boot executable JARs have a special loader and can't be used as dependencies
        - common-lib has NO main() method — it's not an application, it's shared code
    -->

    <dependencies>
        <!-- ═══════════════════════════════════════════════════════════════ -->
        <!-- DEPENDENCY 1: Spring Web                                       -->
        <!-- ═══════════════════════════════════════════════════════════════ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!--
            WHY WE NEED THIS:
            - Provides @RestControllerAdvice (used in GlobalExceptionHandler)
            - Provides ResponseEntity (used in exception handler return types)
            - Provides HttpStatus enum (used in PayflowException)
            - Provides @ExceptionHandler annotation
            WITHOUT this: GlobalExceptionHandler won't compile.
            NOTE: No <version> tag — inherited from parent's Spring Boot BOM.
        -->

        <!-- ═══════════════════════════════════════════════════════════════ -->
        <!-- DEPENDENCY 2: Bean Validation                                  -->
        <!-- ═══════════════════════════════════════════════════════════════ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <!--
            WHY WE NEED THIS:
            - Provides jakarta.validation.ConstraintViolationException
              (caught by GlobalExceptionHandler)
            - Provides @NotNull, @Size, @Email annotations for DTO validation
            - Future DTOs in common-lib may use validation annotations
            WITHOUT this: ConstraintViolationException handler won't compile.
        -->

        <!-- ═══════════════════════════════════════════════════════════════ -->
        <!-- DEPENDENCY 3: Jackson JSON Annotations                         -->
        <!-- ═══════════════════════════════════════════════════════════════ -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
        <!--
            WHY WE NEED THIS:
            - Provides @JsonInclude annotation (used on ApiResponse, ErrorDetail, PagedResponse)
            - @JsonInclude(NON_NULL) tells Jackson: "skip null fields in JSON output"
            - This keeps API responses clean (no "data": null in error responses)
            WITHOUT this: @JsonInclude won't resolve, DTOs won't compile.
            NOTE: We only need jackson-annotations (not full jackson-databind)
                  because we only use annotations here, not ObjectMapper.
        -->
    </dependencies>

    <build>
        <plugins>
            <!-- Do NOT build as Spring Boot executable JAR (this is a library) -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <skip>true</skip>
                </configuration>
            </plugin>
            <!--
                WHY skip=true:
                - The parent POM has spring-boot-maven-plugin configured for ALL modules
                - That plugin normally repackages JARs into executable Spring Boot JARs
                - Executable JARs have a special classloader that breaks dependency resolution
                - common-lib is NOT an application — it has no main() class
                - skip=true says: "Don't repackage me, I'm a plain library JAR"
                WITHOUT this: `mvn install` would fail with "Unable to find main class"
            -->
        </plugins>
    </build>

</project>
```

**Key takeaway:** This POM does 3 things:
1. Inherits versions from parent (no version duplication)
2. Pulls in only the dependencies common-lib actually needs
3. Tells Spring Boot "don't make me into an executable app"

---

### 4.2 ApiResponse.java — Standard Response Wrapper

**File:** `common-lib/src/main/java/com/payflow/common/dto/ApiResponse.java`

**Purpose:** Every single API endpoint in PayFlow returns data wrapped in this class.
This gives consumers (frontend, mobile app, Postman) a predictable structure they
can always rely on.

**Design Pattern:** Factory Method — static methods (`success()`, `error()`) create
instances instead of forcing callers to use the Builder directly.

**JSON Output Examples:**

```json
// Success response (e.g., GET /v1/payments/pay_Hk7mN3xQp2)
{
  "success": true,
  "data": { "id": "pay_Hk7mN3xQp2", "amount": 5000, "status": "CAPTURED" },
  "timestamp": "2026-07-19T14:30:00Z"
}

// Error response (e.g., GET /v1/payments/pay_INVALID)
{
  "success": false,
  "error": { "code": "PAYMENT_NOT_FOUND", "message": "Payment with ID 'pay_INVALID' not found" },
  "timestamp": "2026-07-19T14:30:00Z"
}
```

Notice: In success, there's no `"error"` field. In error, there's no `"data"` field.
That's `@JsonInclude(NON_NULL)` at work — null fields are omitted from JSON.

**Complete Source Code:**

```java
package com.payflow.common.dto;
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^
// Package declaration: this class lives in the "dto" (Data Transfer Object) sub-package.
// DTOs are objects that carry data between layers (controller → client).

import com.fasterxml.jackson.annotation.JsonInclude;
// Jackson annotation: controls which fields appear in JSON output.

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// Lombok annotations: generate boilerplate code at compile time (see below).

import java.time.Instant;
// Java's immutable timestamp class. Always UTC. Example: "2026-07-19T14:30:00Z"
// WHY Instant: timezone-independent, ISO-8601 format, works globally.

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
// WHAT @Data GENERATES (you don't write these — Lombok does it for you):
//   - getter for every field: getSuccess(), getData(), getError(), getTimestamp()
//   - setter for every field: setSuccess(), setData(), setError(), setTimestamp()
//   - toString(): "ApiResponse(success=true, data=..., error=null, timestamp=...)"
//   - equals() and hashCode(): based on ALL fields
//   - @RequiredArgsConstructor (for final fields — none here, so no effect)
// WHY: Eliminates 50+ lines of boilerplate Java code.

@Builder
// WHAT @Builder GENERATES:
//   - A static inner class: ApiResponse.ApiResponseBuilder
//   - Builder methods: .success(true).data(obj).timestamp(Instant.now()).build()
//   - A static method: ApiResponse.builder() that returns the builder
// WHY: Makes object creation readable and prevents constructor parameter confusion.
// EXAMPLE: ApiResponse.builder().success(true).data(payment).timestamp(now).build()

@NoArgsConstructor
// WHAT @NoArgsConstructor GENERATES:
//   - public ApiResponse() {} — a constructor with zero arguments
// WHY: Jackson (JSON deserializer) needs a no-arg constructor to create objects.
// Without this, Jackson can't convert incoming JSON back into ApiResponse objects.

@AllArgsConstructor
// WHAT @AllArgsConstructor GENERATES:
//   - public ApiResponse(boolean success, T data, ErrorDetail error, Instant timestamp)
// WHY: @Builder needs this internally to create objects with all fields set.
// The Builder calls this constructor behind the scenes.

@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields in JSON
// WHAT THIS DOES:
//   - When Jackson serializes this object to JSON, it SKIPS any field that is null.
//   - Success response: data=Payment, error=null → JSON has "data" but NO "error" key
//   - Error response: data=null, error=ErrorDetail → JSON has "error" but NO "data" key
// WHY: Keeps JSON responses clean. Clients don't see irrelevant null fields.
// WITHOUT: Every response would have "data": null or "error": null cluttering the output.

public class ApiResponse<T> {
// WHAT IS <T>?
//   - T is a "generic type parameter" — a placeholder for any type
//   - ApiResponse<PaymentDTO> → T becomes PaymentDTO
//   - ApiResponse<MerchantDTO> → T becomes MerchantDTO
//   - ApiResponse<List<String>> → T becomes List<String>
// WHY: One wrapper class works for ALL entity types. No need for
//       PaymentApiResponse, MerchantApiResponse, etc.

    private boolean success;
    // true = request succeeded, false = request failed
    // This is ALWAYS present (primitives can't be null)

    private T data;
    // The actual payload. Could be a PaymentDTO, MerchantDTO, UserDTO, etc.
    // null on error responses → omitted from JSON by @JsonInclude(NON_NULL)

    private ErrorDetail error;
    // Error information (code + message + optional details)
    // null on success responses → omitted from JSON by @JsonInclude(NON_NULL)

    private Instant timestamp;
    // When this response was generated. Useful for debugging and logging.
    // Always UTC (no timezone confusion)

    /**
     * Create a success response with data.
     * FACTORY METHOD PATTERN: Static method that creates an instance.
     * Caller doesn't need to know about Builder internals.
     */
    public static <T> ApiResponse<T> success(T data) {
        // <T> before return type = this is a generic method
        // The T here matches whatever type the caller passes in
        return ApiResponse.<T>builder()
                .success(true)          // Mark as successful
                .data(data)             // Attach the payload
                .timestamp(Instant.now()) // Current UTC time
                .build();               // Create the ApiResponse object
    }
    // USAGE: ApiResponse.success(paymentDTO)
    // RESULT: {"success":true, "data":{...}, "timestamp":"2026-..."}

    /**
     * Create a success response without data (e.g., delete operations).
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .build();
        // No .data() call → data remains null → omitted from JSON
    }
    // USAGE: ApiResponse.success()  (after DELETE /v1/payments/pay_xxx)
    // RESULT: {"success":true, "timestamp":"2026-..."}

    /**
     * Create an error response.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)         // Mark as failed
                .error(new ErrorDetail(code, message, null))
                // Create ErrorDetail with code+message, no extra details
                .timestamp(Instant.now())
                .build();
    }
    // USAGE: ApiResponse.error("PAYMENT_NOT_FOUND", "Payment with ID x not found")
    // RESULT: {"success":false, "error":{"code":"PAYMENT_NOT_FOUND","message":"..."}, "timestamp":"..."}

    /**
     * Create an error response with details.
     */
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ErrorDetail(code, message, details))
                // details could be a Map of field errors, a list, or any object
                .timestamp(Instant.now())
                .build();
    }
    // USAGE: ApiResponse.error("VALIDATION_ERROR", "Validation failed", fieldErrorsMap)
    // RESULT: {"success":false, "error":{"code":"...","message":"...","details":{"email":"required"}}}
}
```

**How a controller uses this in practice:**

```java
@GetMapping("/v1/payments/{id}")
public ResponseEntity<ApiResponse<PaymentDTO>> getPayment(@PathVariable String id) {
    PaymentDTO payment = paymentService.findById(id);
    return ResponseEntity.ok(ApiResponse.success(payment));
    // Returns: HTTP 200 with {"success":true, "data":{payment object}, "timestamp":"..."}
}

@DeleteMapping("/v1/payments/{id}")
public ResponseEntity<ApiResponse<Void>> cancelPayment(@PathVariable String id) {
    paymentService.cancel(id);
    return ResponseEntity.ok(ApiResponse.success());
    // Returns: HTTP 200 with {"success":true, "timestamp":"..."}
}
```

---

### 4.3 ErrorDetail.java — Error Response Structure

**File:** `common-lib/src/main/java/com/payflow/common/dto/ErrorDetail.java`

**Purpose:** When something goes wrong, the error response contains this object.
It gives the client a machine-readable code (for programmatic handling) and a
human-readable message (for display to users or developers).

**Design Decision:** `details` is typed as `Object` (not `String`) so it can hold:
- A `Map<String, String>` of field validation errors
- A `List<String>` of multiple error messages
- Any structured data the handler wants to include

**Complete Source Code:**

```java
package com.payflow.common.dto;
// Lives in the same "dto" package as ApiResponse (they're used together)

import com.fasterxml.jackson.annotation.JsonInclude;
// Same Jackson annotation — skip null fields in JSON output

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
// Lombok: generates getters, setters, toString, equals, hashCode, constructors

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
// Generates: getCode(), setCode(), getMessage(), setMessage(), getDetails(), setDetails()
//            toString(), equals(), hashCode()

@NoArgsConstructor
// Generates: public ErrorDetail() {}
// WHY: Jackson needs this for JSON deserialization (creating object from JSON)

@AllArgsConstructor
// Generates: public ErrorDetail(String code, String message, Object details)
// WHY: Used by ApiResponse.error() to create ErrorDetail in one line:
//      new ErrorDetail("PAYMENT_DECLINED", "Payment declined", null)

@JsonInclude(JsonInclude.Include.NON_NULL)
// EFFECT: If details is null, JSON output won't have a "details" key at all.
// Simple errors: {"code":"NOT_FOUND", "message":"..."}        — no details key
// Complex errors: {"code":"VALIDATION_ERROR", "message":"...", "details":{...}}

public class ErrorDetail {

    /** Machine-readable error code (e.g., "PAYMENT_DECLINED", "INVALID_API_KEY") */
    private String code;
    // NAMING CONVENTION: UPPER_SNAKE_CASE (like HTTP status codes but more specific)
    // Examples: PAYMENT_NOT_FOUND, VALIDATION_ERROR, DUPLICATE_EMAIL, RATE_LIMIT_EXCEEDED
    // WHY machine-readable: Frontend can switch on this code to show different UI
    //   if (error.code === "RATE_LIMIT_EXCEEDED") showRetryButton();

    /** Human-readable error message */
    private String message;
    // Written for developers/users. Full English sentences.
    // Example: "Payment with ID 'pay_abc123' not found"
    // NOT for programmatic use (messages may change; codes won't)

    /** Additional context (optional) — can be any object */
    private Object details;
    // WHY Object and not String?
    // - For validation errors: Map<String, String> = {"email": "must not be blank", "amount": "must be positive"}
    // - For rate limit errors: Map<String, Integer> = {"retry_after_seconds": 30}
    // - For complex errors: custom objects with extra context
    // Jackson serializes ANY object to JSON, so this is flexible.
    // NULL when there's nothing extra to say (omitted from JSON by @JsonInclude)
}
```

**How it appears in a real API response:**

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "email": "must be a valid email address",
      "amount": "must be greater than 0"
    }
  },
  "timestamp": "2026-07-19T14:30:00Z"
}
```

---

### 4.4 PagedResponse.java — Paginated List Response

**File:** `common-lib/src/main/java/com/payflow/common/dto/PagedResponse.java`

**Purpose:** When an API returns a LIST of items (payments, merchants, transactions),
we don't return ALL of them at once (could be millions). Instead, we return one "page"
at a time with metadata about how many total items exist.

**Design Pattern:** Inner Static Class — `PaginationInfo` is nested inside `PagedResponse`
because it's ONLY ever used as part of a paged response. This keeps the class hierarchy
clean and communicates "these belong together."

**JSON Output Example:**

```json
{
  "success": true,
  "data": [
    { "id": "pay_abc", "amount": 5000 },
    { "id": "pay_def", "amount": 3000 }
  ],
  "pagination": {
    "total": 150,
    "page": 1,
    "perPage": 20,
    "totalPages": 8
  },
  "timestamp": "2026-07-19T14:30:00Z"
}
```

**Complete Source Code:**

```java
package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
// List<T> holds the page of items (e.g., List<PaymentDTO>)

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
// Same annotations as ApiResponse — same reasons apply.
// @Builder lets us construct PagedResponse fluently.
// @JsonInclude(NON_NULL) keeps JSON clean.

public class PagedResponse<T> {
// <T> = the type of items in the list.
// PagedResponse<PaymentDTO> → data is List<PaymentDTO>
// PagedResponse<MerchantDTO> → data is List<MerchantDTO>

    private boolean success;
    // Always true for paged responses (errors use ApiResponse instead)

    private List<T> data;
    // The actual page of items. Size = perPage (or less for the last page).
    // Example: 20 PaymentDTO objects when perPage=20

    private PaginationInfo pagination;
    // Metadata telling the client: how many items exist, which page this is, etc.
    // Client uses this to render "Page 1 of 8" and enable/disable next/prev buttons.

    private Instant timestamp;
    // When this response was generated (UTC)

    /**
     * FACTORY METHOD: Create a paged response from a data list and pagination info.
     *
     * @param data    The items for this page (e.g., 20 payments)
     * @param total   Total items in the database matching the query (e.g., 150)
     * @param page    Current page number (1-based: first page = 1)
     * @param perPage Items per page (e.g., 20)
     */
    public static <T> PagedResponse<T> of(List<T> data, long total, int page, int perPage) {
        int totalPages = (int) Math.ceil((double) total / perPage);
        // MATH EXPLANATION:
        //   total=150, perPage=20 → 150/20 = 7.5 → ceil(7.5) = 8 pages
        //   total=100, perPage=20 → 100/20 = 5.0 → ceil(5.0) = 5 pages
        //   total=0, perPage=20   → 0/20   = 0.0 → ceil(0.0) = 0 pages
        //   (double) cast prevents integer division: 150/20=7 (wrong!) vs 150.0/20=7.5 (right!)

        return PagedResponse.<T>builder()
                .success(true)
                .data(data)
                .pagination(new PaginationInfo(total, page, perPage, totalPages))
                .timestamp(Instant.now())
                .build();
    }
    // USAGE: PagedResponse.of(paymentList, 150, 1, 20)
    // RESULT: {"success":true, "data":[...], "pagination":{"total":150,...}, "timestamp":"..."}

    /**
     * INNER STATIC CLASS: Holds pagination metadata.
     *
     * WHY an inner class?
     * - PaginationInfo is ONLY used inside PagedResponse
     * - Keeps it self-contained (no separate file needed)
     * - Java serializes it as a nested JSON object automatically
     *
     * WHY static?
     * - Static inner classes don't hold a reference to the outer class
     * - More memory efficient, can be created independently
     * - Best practice for data holder classes
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {

        private long total;
        // Total number of items matching the query across ALL pages.
        // Example: 150 payments total in the database
        // WHY long (not int): databases can have billions of rows

        private int page;
        // Current page number (1-based).
        // Page 1 = first page, Page 2 = second page, etc.
        // WHY 1-based: more intuitive for humans and API consumers

        private int perPage;
        // How many items per page.
        // Default: 20. Client can override: ?per_page=50

        private int totalPages;
        // How many pages exist total.
        // Calculated: ceil(total / perPage)
        // Client uses this to know when to stop paginating
    }
}
```

**How a service uses this:**

```java
@GetMapping("/v1/payments")
public ResponseEntity<PagedResponse<PaymentDTO>> listPayments(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int perPage) {

    List<PaymentDTO> payments = paymentService.findPage(page, perPage);
    long total = paymentService.countAll();

    return ResponseEntity.ok(PagedResponse.of(payments, total, page, perPage));
}
```

---

### 4.5 Exception Hierarchy — Visual Overview

Before we look at each exception class, here's how they relate to each other:

```
                         java.lang.RuntimeException
                                    │
                                    │ extends
                                    ▼
                          ┌─────────────────────┐
                          │  PayflowException    │ ← BASE (abstract-ish parent)
                          │─────────────────────│
                          │ - errorCode: String  │
                          │ - httpStatus: HttpStatus │
                          │ - message (inherited)│
                          └─────────┬───────────┘
                                    │
                    ┌───────────────┼───────────────────┐
                    │               │                   │
                    ▼               ▼                   ▼
    ┌───────────────────┐ ┌─────────────────────┐ ┌──────────────────────────┐
    │ResourceNotFound   │ │DuplicateResource    │ │InvalidStateTransition    │
    │Exception          │ │Exception            │ │Exception                 │
    │───────────────────│ │─────────────────────│ │──────────────────────────│
    │HTTP 404 Not Found │ │HTTP 409 Conflict    │ │HTTP 400 Bad Request      │
    │"Payment not found"│ │"Email already taken"│ │"Cannot capture VOIDED"   │
    └───────────────────┘ └─────────────────────┘ └──────────────────────────┘
```

**Design Pattern:** Template Method (sort of) — the base class defines the structure
(errorCode + message + httpStatus), and each subclass fills in specific values.
The GlobalExceptionHandler handles ALL of them uniformly via the base type.

**WHY RuntimeException (unchecked) and not Exception (checked)?**
- Checked exceptions force `throws` declarations on every method in the call chain
- In Spring Boot, controller methods can't easily declare checked exceptions
- RuntimeException propagates naturally up to the @RestControllerAdvice handler
- This is the standard Spring Boot convention

---

### 4.6 PayflowException.java — The Base Exception

**File:** `common-lib/src/main/java/com/payflow/common/exception/PayflowException.java`

**Purpose:** Base exception class that ALL PayFlow business exceptions extend.
Carries three pieces of information:
1. Error code (machine-readable, e.g., "PAYMENT_DECLINED")
2. Message (human-readable, e.g., "Payment was declined by the bank")
3. HTTP status (which HTTP code to return, e.g., 404, 409, 400)

**Complete Source Code:**

```java
package com.payflow.common.exception;
// All exceptions live in the "exception" sub-package

import lombok.Getter;
// Lombok: generates getter methods for annotated fields

import org.springframework.http.HttpStatus;
// Spring's enum for HTTP status codes: OK(200), NOT_FOUND(404), CONFLICT(409), etc.

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
// WHAT @Getter GENERATES:
//   - public String getErrorCode() { return this.errorCode; }
//   - public HttpStatus getHttpStatus() { return this.httpStatus; }
// WHY: GlobalExceptionHandler calls ex.getErrorCode() and ex.getHttpStatus()
//       to build the error response. Without @Getter, we'd write these manually.
// NOTE: getMessage() is already inherited from RuntimeException — no need to generate it.

public class PayflowException extends RuntimeException {
// extends RuntimeException:
//   - Makes this an "unchecked" exception (no `throws` required)
//   - RuntimeException already has: message, cause, stacktrace
//   - We ADD: errorCode and httpStatus

    private final String errorCode;
    // Machine-readable code that NEVER changes between versions.
    // Frontend/API consumers can rely on this for programmatic handling.
    // Convention: UPPER_SNAKE_CASE (e.g., "PAYMENT_NOT_FOUND", "RATE_LIMIT_EXCEEDED")
    // "final" = assigned once in constructor, never changed (immutable)

    private final HttpStatus httpStatus;
    // Which HTTP status code to return in the response.
    // Examples: HttpStatus.NOT_FOUND (404), HttpStatus.CONFLICT (409), HttpStatus.BAD_REQUEST (400)
    // GlobalExceptionHandler uses this: ResponseEntity.status(ex.getHttpStatus())
    // "final" = immutable (a 404 exception always stays 404)

    public PayflowException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        // super(message) calls RuntimeException(String message)
        // This sets the inherited getMessage() method to return our message.

        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public PayflowException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        // This overload accepts a "cause" — the original exception that triggered this one.
        // Example: catch (SQLException e) { throw new PayflowException("DB_ERROR", "...", 500, e); }
        // The original SQLException stacktrace is preserved for debugging.

        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
```

**Key insight:** You never throw `PayflowException` directly. You always throw one of
its subclasses (ResourceNotFoundException, DuplicateResourceException, etc.) which
pre-fill the httpStatus so callers don't need to think about it.

---

### 4.7 ResourceNotFoundException.java — HTTP 404

**File:** `common-lib/src/main/java/com/payflow/common/exception/ResourceNotFoundException.java`

**Purpose:** Thrown when a user requests something that doesn't exist.
Always results in HTTP 404 Not Found.

**When to throw this:**
- `GET /v1/payments/pay_INVALID` → payment not found
- `GET /v1/merchants/merch_INVALID` → merchant not found
- `DELETE /v1/api-keys/key_EXPIRED` → API key not found

**Complete Source Code:**

```java
package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource is not found.
 * Returns HTTP 404.
 *
 * Example usage:
 *   throw new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment with ID pay_xyz not found");
 */
public class ResourceNotFoundException extends PayflowException {
// extends PayflowException:
//   - Inherits errorCode, httpStatus, message
//   - GlobalExceptionHandler catches it via: @ExceptionHandler(PayflowException.class)
//   - No need for a separate handler for each subclass!

    /**
     * Constructor 1: Provide your own error code and message.
     * For when you want full control over the error details.
     */
    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.NOT_FOUND);
        // Always passes HttpStatus.NOT_FOUND (404) to the parent.
        // The caller doesn't need to remember "404" — just throw this exception.
    }
    // USAGE: throw new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment pay_xyz not found");

    /**
     * Constructor 2: Convenience constructor that builds the error code and message for you.
     * Just tell it what resource, what field, and what value.
     */
    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(
                resourceName.toUpperCase() + "_NOT_FOUND",
                // Builds error code: "Payment" → "PAYMENT_NOT_FOUND"
                //                    "Merchant" → "MERCHANT_NOT_FOUND"

                String.format("%s with %s '%s' not found", resourceName, fieldName, fieldValue),
                // Builds message: "Payment with id 'pay_xyz' not found"
                //                 "Merchant with email 'test@x.com' not found"

                HttpStatus.NOT_FOUND
        );
    }
    // USAGE: throw new ResourceNotFoundException("Payment", "id", "pay_xyz");
    // RESULT: errorCode="PAYMENT_NOT_FOUND", message="Payment with id 'pay_xyz' not found", status=404
}
```

**In a service class:**

```java
public PaymentDTO findById(String paymentId) {
    return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    // If payment doesn't exist → throws 404 → GlobalExceptionHandler catches → returns JSON error
}
```

---

### 4.8 DuplicateResourceException.java — HTTP 409

**File:** `common-lib/src/main/java/com/payflow/common/exception/DuplicateResourceException.java`

**Purpose:** Thrown when trying to create something that already exists.
Always results in HTTP 409 Conflict.

**When to throw this:**
- Registering with an email that's already taken
- Creating a merchant with a business name that already exists
- Submitting a payment with a duplicate idempotency key

**Complete Source Code:**

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
// extends PayflowException → caught by the same @ExceptionHandler(PayflowException.class)

    public DuplicateResourceException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
        // HttpStatus.CONFLICT = 409
        // 409 means "your request conflicts with the current state of the server"
        // (i.e., the resource you're trying to create already exists)
    }
    // USAGE: throw new DuplicateResourceException("DUPLICATE_EMAIL", "Email 'x@y.com' is already registered");
}
```

**In a service class:**

```java
public UserDTO register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new DuplicateResourceException(
            "DUPLICATE_EMAIL",
            "Email '" + request.getEmail() + "' is already registered"
        );
    }
    // ... create user
}
```

---

### 4.9 InvalidStateTransitionException.java — HTTP 400 (State Machine Error)

**File:** `common-lib/src/main/java/com/payflow/common/exception/InvalidStateTransitionException.java`

**Purpose:** Thrown when someone tries to perform an action that's not allowed
given the payment's current state. This is the state machine's "guard."

**When to throw this:**
- Trying to CAPTURE a payment that is VOIDED (can't capture cancelled money)
- Trying to REFUND a payment that is still PROCESSING (not captured yet)
- Trying to VOID a payment that is already SETTLED (money already transferred)

**Complete Source Code:**

```java
package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting an invalid state transition on a payment.
 * Returns HTTP 400 Bad Request.
 *
 * Example: Trying to capture a payment that is already VOIDED.
 *   "Cannot capture payment. Current status: VOIDED. Capture only works on AUTHORIZED payments."
 */
public class InvalidStateTransitionException extends PayflowException {
// extends PayflowException → caught by GlobalExceptionHandler automatically

    public InvalidStateTransitionException(String currentState, String attemptedAction) {
        super(
                "INVALID_STATE_TRANSITION",
                // Error code is always the same for this exception type.
                // Frontend knows: if code == "INVALID_STATE_TRANSITION" → show state error UI

                String.format("Cannot %s. Current status: '%s'. This action is not allowed in this state.",
                        attemptedAction, currentState),
                // Human-readable message explaining what went wrong:
                // "Cannot capture. Current status: 'VOIDED'. This action is not allowed in this state."
                // "Cannot refund. Current status: 'PROCESSING'. This action is not allowed in this state."

                HttpStatus.BAD_REQUEST
                // 400 Bad Request: the client made an error (not the server)
                // The request itself is syntactically valid, but logically wrong given current state
        );
    }
    // USAGE: throw new InvalidStateTransitionException("VOIDED", "capture");
    // RESULT: {"success":false, "error":{"code":"INVALID_STATE_TRANSITION",
    //          "message":"Cannot capture. Current status: 'VOIDED'. This action is not allowed in this state."}}
}
```

**In a service class (state machine guard):**

```java
public PaymentDTO capturePayment(String paymentId) {
    Payment payment = findPaymentOrThrow(paymentId);

    if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
        throw new InvalidStateTransitionException(
            payment.getStatus().name(),  // "VOIDED", "SETTLED", etc.
            "capture"                     // What the user tried to do
        );
    }
    // ... proceed with capture
}
```

---

### 4.10 GlobalExceptionHandler.java — Catches ALL Errors

**File:** `common-lib/src/main/java/com/payflow/common/exception/GlobalExceptionHandler.java`

**Purpose:** This is the SINGLE class that converts ALL exceptions into clean JSON
error responses. Without it, Spring Boot returns ugly HTML error pages or raw stacktraces.

**How it works:**
```
1. Controller throws an exception (any exception)
2. Spring sees the @RestControllerAdvice annotation on this class
3. Spring finds the @ExceptionHandler method that matches the exception type
4. That method creates an ApiResponse.error(...) and returns it as JSON
5. Client receives a clean, consistent error response
```

**Design Pattern:** This implements the "Centralized Exception Handling" pattern.
Instead of try-catch blocks in every controller, we handle ALL errors in ONE place.

**Complete Source Code:**

```java
package com.payflow.common.exception;

import com.payflow.common.dto.ApiResponse;
// Our standard response wrapper (used to wrap error responses too)

import jakarta.validation.ConstraintViolationException;
// Thrown when @Size, @Min, @Max annotations on PATH VARIABLES or QUERY PARAMS are violated.
// Different from MethodArgumentNotValidException (which is for @Valid on request BODY).

import lombok.extern.slf4j.Slf4j;
// Lombok: creates a private static final Logger named "log".
// Equivalent to: private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// ResponseEntity = HTTP response with status code + headers + body
// We use it to set the exact HTTP status (400, 404, 409, 500) per error type.

import org.springframework.web.bind.MethodArgumentNotValidException;
// Thrown when @Valid on a @RequestBody fails validation.
// Example: @NotNull field is null, @Size(max=50) field has 100 chars, @Email is invalid.

import org.springframework.web.bind.MissingRequestHeaderException;
// Thrown when a @RequestHeader(required=true) header is not provided.
// Example: Idempotency-Key header required but not sent by client.

import org.springframework.web.bind.annotation.ExceptionHandler;
// Marks a method as an exception handler. Spring routes matching exceptions to it.

import org.springframework.web.bind.annotation.RestControllerAdvice;
// Combines @ControllerAdvice + @ResponseBody:
//   - @ControllerAdvice: "apply this to ALL controllers in the application"
//   - @ResponseBody: "serialize return value as JSON (not view name)"

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
// WHAT @Slf4j GENERATES:
//   private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
// WHY: We log every error for debugging. Production logs help trace issues.
// USAGE: log.warn("..."), log.error("...", exception)

@RestControllerAdvice
// WHAT THIS DOES:
//   1. Makes this class apply to EVERY @RestController in the application
//   2. Spring scans for @ExceptionHandler methods in this class
//   3. When ANY controller throws an exception, Spring checks here FIRST
//   4. Return values are automatically serialized to JSON (like @ResponseBody)
// WHY: Centralized error handling. Write once, applies to all 50+ endpoints.
// WITHOUT: Each controller would need its own try-catch blocks — code duplication nightmare.

public class GlobalExceptionHandler {

    // ═══════════════════════════════════════════════════════════════════════
    // HANDLER 1: Our custom business exceptions (PayflowException family)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle our custom business exceptions (PayflowException and subclasses).
     */
    @ExceptionHandler(PayflowException.class)
    // WHAT THIS MEANS:
    //   "When a PayflowException (or ANY subclass) is thrown, call THIS method."
    //   Subclasses include: ResourceNotFoundException, DuplicateResourceException,
    //   InvalidStateTransitionException — ALL are caught here.
    //   This is POLYMORPHISM in action: one handler for the entire exception hierarchy.

    public ResponseEntity<ApiResponse<Void>> handlePayflowException(PayflowException ex) {
        // ResponseEntity<ApiResponse<Void>>:
        //   - ResponseEntity: lets us set HTTP status code (404, 409, 400)
        //   - ApiResponse<Void>: error response has no "data" (Void = nothing)

        log.warn("Business error: [{}] {}", ex.getErrorCode(), ex.getMessage());
        // Log at WARN level (not ERROR) because business errors are expected behavior.
        // "User tried to access non-existent payment" = expected, not a server problem.
        // Example log: "Business error: [PAYMENT_NOT_FOUND] Payment with id 'pay_xyz' not found"

        return ResponseEntity
                .status(ex.getHttpStatus())   // 404, 409, or 400 depending on exception type
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
                // Wraps the error in our standard format:
                // {"success":false, "error":{"code":"PAYMENT_NOT_FOUND","message":"..."}, "timestamp":"..."}
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HANDLER 2: Bean Validation errors (@Valid on @RequestBody)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle validation errors (@Valid on request body).
     * Example: @NotNull field is null, @Size exceeded, @Email invalid format.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    // WHEN IS THIS THROWN?
    //   - Controller has: @PostMapping + @Valid @RequestBody CreatePaymentRequest request
    //   - Request body JSON has: {"amount": -5, "email": "not-an-email"}
    //   - @Min(1) on amount fails, @Email on email fails
    //   - Spring throws MethodArgumentNotValidException BEFORE your controller code runs

    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        // We'll collect ALL field errors into a map: {"fieldName": "error message"}

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        // getBindingResult() = the validation result object
        // getFieldErrors() = list of all fields that failed validation
        // For each: field name → error message
        // Result: {"amount": "must be greater than 0", "email": "must be a valid email"}

        log.warn("Validation error: {}", fieldErrors);
        // Log which fields failed (helpful for debugging bad requests)

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)  // Always 400 for validation errors
                .body(ApiResponse.error("VALIDATION_ERROR", "Request validation failed", fieldErrors));
                // The fieldErrors map becomes the "details" object in the JSON response:
                // {"success":false, "error":{"code":"VALIDATION_ERROR", "message":"...",
                //  "details":{"amount":"must be greater than 0","email":"must be a valid email"}}}
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HANDLER 3: Constraint violations (@Size on path/query params)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle constraint violations (e.g., @Size on path variable).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    // WHEN IS THIS THROWN?
    //   - Controller has: @GetMapping("/v1/payments/{id}")
    //   - Parameter has: @Size(min=10, max=20) @PathVariable String id
    //   - Client sends: GET /v1/payments/x (too short!)
    //   - Hibernate Validator throws ConstraintViolationException
    // DIFFERENCE from Handler 2:
    //   - Handler 2 = @RequestBody validation (JSON body)
    //   - Handler 3 = path variable / query parameter validation

    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)  // 400 Bad Request
                .body(ApiResponse.error("VALIDATION_ERROR", ex.getMessage()));
                // getMessage() already contains a readable error like:
                // "getPayment.id: size must be between 10 and 20"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HANDLER 4: Missing required HTTP headers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle missing required headers (e.g., Idempotency-Key not provided).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    // WHEN IS THIS THROWN?
    //   - Controller has: @RequestHeader("Idempotency-Key") String idempotencyKey
    //   - Client sends request WITHOUT the Idempotency-Key header
    //   - Spring throws MissingRequestHeaderException before your code runs
    // WHY A SEPARATE HANDLER?
    //   - Missing headers are common in payment APIs (idempotency keys are required)
    //   - We want a clear error message: "Required header 'Idempotency-Key' is missing"

    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing header: {}", ex.getHeaderName());
        // ex.getHeaderName() = "Idempotency-Key" (the name of the missing header)

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)  // 400 Bad Request
                .body(ApiResponse.error("MISSING_HEADER",
                        String.format("Required header '%s' is missing", ex.getHeaderName())));
                // Clear message telling the client exactly which header they forgot
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HANDLER 5: Catch-all for unexpected errors (the safety net)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Catch-all for unexpected errors (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    // WHAT THIS CATCHES:
    //   - NullPointerException (bug in our code)
    //   - SQLException (database connection lost)
    //   - Any exception NOT caught by the handlers above
    // WHY: This is the SAFETY NET. Without it, Spring returns ugly HTML error pages.
    // IMPORTANT: This must be the LAST handler (most general). Spring tries handlers
    //            from most specific to most general.

    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        // Log at ERROR level (not WARN) because this is unexpected.
        // The full stacktrace is logged (notice the ", ex" parameter — Slf4j logs the stacktrace).
        // This is for OUR debugging — we need to know what broke.

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)  // 500 Internal Server Error
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
                // SECURITY: We do NOT expose the real exception message to the client!
                // Real message might reveal: SQL queries, file paths, internal class names
                // Instead: generic "something went wrong" message.
                // The real details are in our server logs (for our eyes only).
    }
}
```

**Handler priority order (Spring tries from top to bottom):**

```
Exception thrown
    │
    ├─ Is it a PayflowException (or subclass)? → Handler 1 (returns custom status)
    ├─ Is it MethodArgumentNotValidException?  → Handler 2 (returns 400 + field errors)
    ├─ Is it ConstraintViolationException?     → Handler 3 (returns 400)
    ├─ Is it MissingRequestHeaderException?    → Handler 4 (returns 400 + header name)
    └─ Anything else?                          → Handler 5 (returns 500, logs full error)
```

**Why this class is in common-lib:**
Every service (identity, payment, merchant, etc.) needs the same error handling.
By putting it in common-lib, Spring Boot auto-discovers it (via component scanning)
in every service that depends on common-lib. Write once, works in all 7 services.

---

### 4.11 IdGenerator.java — Short, Unique, Prefixed IDs

**File:** `common-lib/src/main/java/com/payflow/common/util/IdGenerator.java`

**Purpose:** Generates short, URL-friendly IDs like `pay_Hk7mN3xQp2` instead of
long UUIDs like `550e8400-e29b-41d4-a716-446655440000`.

**Why not UUID?**
| Property | UUID | Our ID |
|----------|------|--------|
| Length | 36 characters | 14-16 characters |
| Readability | `550e8400-e29b-41d4...` (what entity?) | `pay_Hk7mN3xQp2` (it's a payment!) |
| URL friendliness | Long, ugly URLs | Short, clean URLs |
| Entity type visible? | No | Yes (prefix tells you) |
| Uniqueness | Extremely high (128-bit) | Very high (62^10 ≈ 10^17) |

**Collision Probability Math:**

```
Alphabet: A-Z (26) + a-z (26) + 0-9 (10) = 62 characters
ID length: 10 characters
Possible IDs: 62^10 = 839,299,365,868,340,224 ≈ 839 TRILLION

Birthday problem formula: p(collision) ≈ n² / (2 × N)
  where n = number of IDs generated, N = total possible IDs

If we generate 1 BILLION IDs:
  p = (10^9)² / (2 × 8.39×10^17) = 10^18 / 1.68×10^18 ≈ 0.6 (60% chance — too high!)

If we generate 1 MILLION IDs (realistic for PayFlow):
  p = (10^6)² / (2 × 8.39×10^17) = 10^12 / 1.68×10^18 ≈ 0.0000006 (0.00006% — negligible)

CONCLUSION: At realistic scale (millions of payments), collision risk is virtually zero.
For extreme scale (billions), use UUID or longer IDs.
```

**Complete Source Code:**

```java
package com.payflow.common.util;
// "util" package = utility classes (stateless helper methods)

import java.security.SecureRandom;
// WHY SecureRandom instead of Random?
//   - Random uses a predictable algorithm (linear congruential generator)
//   - If someone knows the seed, they can predict ALL future IDs
//   - SecureRandom uses OS entropy (hardware noise, timing jitter)
//   - IDs become UNPREDICTABLE — important for security
//   - Attackers can't guess payment IDs or enumerate them
// TRADEOFF: SecureRandom is slightly slower than Random, but for ID generation
//           (not called millions of times per second), the difference is negligible.

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
// WHY not a @Component or @Service?
//   - This class has only STATIC methods (no instance state)
//   - You call IdGenerator.paymentId() directly (no need to inject)
//   - Utility classes don't need Spring's dependency injection
//   - Simpler to use: no @Autowired, no constructor injection needed

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    // 62 characters: 26 uppercase + 26 lowercase + 10 digits
    // WHY these specific characters?
    //   - All are URL-safe (no encoding needed in URLs)
    //   - All are copy-paste safe (no invisible characters)
    //   - Case-sensitive: 'A' and 'a' are different → more combinations in less chars
    //   - No special characters: no confusion with /, +, = (unlike Base64)

    private static final SecureRandom RANDOM = new SecureRandom();
    // Single instance shared across all calls (thread-safe).
    // WHY static: creating a new SecureRandom each time would be wasteful.
    // WHY final: this reference never changes (though its internal state does).

    private static final int ID_LENGTH = 10;
    // 10 random characters per ID.
    // Total ID length = prefix + underscore + 10 = "pay_" + 10 = 14 chars
    // For "merch_": 6 + 10 = 16 chars

    /**
     * Generate a random ID with given prefix.
     * Example: generateId("pay") → "pay_Hk7mN3xQp2"
     */
    public static String generateId(String prefix) {
        StringBuilder sb = new StringBuilder(prefix.length() + 1 + ID_LENGTH);
        // WHY StringBuilder with capacity?
        //   - We know exactly how long the result will be
        //   - Pre-allocating avoids internal array resizing
        //   - Micro-optimization, but good practice
        //   - capacity = prefix length + 1 (underscore) + 10 (random chars)

        sb.append(prefix).append('_');
        // Start with prefix and underscore: "pay_"

        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            // RANDOM.nextInt(62) → random number 0-61
            // ALPHABET.charAt(randomIndex) → picks a random character
            // Repeat 10 times → "Hk7mN3xQp2"
        }
        return sb.toString();
        // Final result: "pay_Hk7mN3xQp2"
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

**Usage in a service:**

```java
@Service
public class PaymentService {
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setId(IdGenerator.paymentId());  // "pay_Hk7mN3xQp2"
        payment.setOrderId(IdGenerator.orderId()); // "ord_LkR3d9xF2m"
        // ... save to database
        return payment;
    }
}
```

---

### 4.12 PaymentStatus.java — Payment State Machine

**File:** `common-lib/src/main/java/com/payflow/common/constant/PaymentStatus.java`

**Purpose:** Defines ALL possible states a payment can be in. This is the heart of
the payment lifecycle — the "state machine" that governs what can happen to a payment.

**State Machine Diagram:**

```
                    ┌─────────────────────────────────────────────────────┐
                    │           PAYMENT STATE MACHINE                      │
                    └─────────────────────────────────────────────────────┘

    Customer creates                Bank holds                  Merchant confirms
    payment order                   customer's money            money transfer
         │                               │                           │
         ▼                               ▼                           ▼
    ┌─────────┐    submit     ┌────────────┐   approve    ┌────────────┐
    │ CREATED ├──────────────►│ PROCESSING ├─────────────►│ AUTHORIZED │
    └────┬────┘               └──────┬─────┘              └──┬────┬────┘
         │                           │                       │    │
         │ 30min timeout             │ bank declines         │    │ merchant cancels
         │                           │                       │    │ (releases hold)
         ▼                           ▼                       │    ▼
    ┌─────────┐               ┌────────────┐               │  ┌────────┐
    │ EXPIRED │               │   FAILED   │               │  │ VOIDED │
    └─────────┘               └────────────┘               │  └────────┘
                                                           │
                                          merchant         │   7-day auth timeout
                                          captures         │         │
                                               │           │         ▼
                                               ▼           │   ┌─────────┐
                                          ┌──────────┐     │   │ EXPIRED │
                                          │ CAPTURED │     │   └─────────┘
                                          └────┬─────┘     │
                                               │           │
                              end-of-day       │           │
                              settlement       │           │
                                   │           │           │
                                   ▼           │           │
                              ┌──────────┐     │           │
                              │ SETTLED  │     │           │
                              └──────────┘     │           │
                                               │
                              customer         │
                              requests         │
                              refund           │
                                   │           │
                                   ▼           │
                              ┌──────────┐     │
                              │ REFUNDED │◄────┘ (only from CAPTURED)
                              └──────────┘
```

**Valid Transitions Summary:**
| From State | To State | Trigger |
|-----------|----------|---------|
| CREATED | PROCESSING | Customer submits payment details |
| CREATED | EXPIRED | 30-minute timeout (no action from customer) |
| PROCESSING | AUTHORIZED | Bank approves and holds funds |
| PROCESSING | FAILED | Bank declines or error occurs |
| AUTHORIZED | CAPTURED | Merchant confirms the payment |
| AUTHORIZED | VOIDED | Merchant cancels (hold released) |
| AUTHORIZED | EXPIRED | 7-day authorization timeout |
| CAPTURED | SETTLED | End-of-day batch settlement |
| CAPTURED | REFUNDED | Customer requests and receives refund |

**Complete Source Code:**

```java
package com.payflow.common.constant;
// "constant" package = values that never change at runtime (enums, constants)

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
// WHY AN ENUM (not String constants)?
//   1. TYPE SAFETY: you can't accidentally write "CRAETED" (typo) — compiler catches it
//   2. IDE AUTOCOMPLETE: type "PaymentStatus." and see all options
//   3. SWITCH SAFETY: Java warns if your switch doesn't cover all enum values
//   4. SERIALIZATION: Jackson automatically serializes to "CREATED", "PROCESSING", etc.
//   5. DATABASE: JPA stores enum names as strings — same values everywhere
//
// WHY not String constants like: public static final String CREATED = "CREATED"?
//   - No type safety (any String can be passed where a status is expected)
//   - Can't use in switch statements as effectively
//   - No compiler protection against typos
//   - Methods can't restrict parameters to valid values only

    /** Order created, waiting for customer to submit payment details */
    CREATED,
    // INITIAL STATE: Every payment starts here.
    // Merchant called POST /v1/payments to create a payment intent.
    // Customer hasn't entered their card/UPI details yet.
    // TIMEOUT: If customer doesn't act within 30 minutes → EXPIRED

    /** Payment submitted, talking to bank (customer is waiting) */
    PROCESSING,
    // Customer submitted payment details (card number, UPI ID, etc.)
    // Our system is now communicating with the bank/payment network.
    // This state typically lasts 2-30 seconds.
    // Customer sees a "Processing..." spinner on the checkout page.

    /** Bank approved, money is HELD on customer's card (not yet deducted) */
    AUTHORIZED,
    // Bank said "YES" — the money EXISTS and is RESERVED (held/blocked).
    // Customer's available balance decreases, but money hasn't left their account.
    // Think of it like a hotel putting a "hold" on your credit card at check-in.
    // Merchant must CAPTURE within 7 days, or the hold expires.

    /** Merchant confirmed, money is DEDUCTED from customer */
    CAPTURED,
    // Merchant said "ship the goods, take the money."
    // Money is now ACTUALLY deducted from customer's account.
    // At this point, the customer HAS been charged.
    // Next step: end-of-day settlement sends money to merchant's bank.

    /** Money transferred to merchant's bank account (end of day batch) */
    SETTLED,
    // FINAL STATE (happy path).
    // End-of-day batch process transferred money from PayFlow to merchant's bank.
    // The full cycle is complete: customer paid → merchant received.

    /** Merchant cancelled before capture (hold released) */
    VOIDED,
    // Merchant cancelled the payment BEFORE capturing.
    // The hold on customer's card is released — they get their available balance back.
    // Example: customer cancelled order before shipping.
    // No money moved — customer was never charged.

    /** Money returned to customer after capture */
    REFUNDED,
    // Customer requested a refund AFTER being charged (post-capture).
    // Money flows BACK from PayFlow/merchant to customer.
    // This is an actual money transfer (unlike VOID which just releases a hold).
    // Can only happen from CAPTURED state (can't refund what wasn't charged).

    /** Bank declined or error occurred */
    FAILED,
    // TERMINAL STATE (unhappy path).
    // Bank said "NO" — reasons: insufficient funds, expired card, fraud detection.
    // Or: network error, bank timeout, invalid card number.
    // Customer needs to try again with different payment method.

    /** Customer didn't complete in time (30 min for order, 7 days for auth) */
    EXPIRED
    // TERMINAL STATE (timeout).
    // Two scenarios:
    //   1. CREATED → EXPIRED: Customer didn't submit payment within 30 minutes
    //   2. AUTHORIZED → EXPIRED: Merchant didn't capture within 7 days
    // Once expired, the payment is dead. Must create a new one.
}
```

**How the state machine is enforced in code:**

```java
// In PaymentService.java
public void capturePayment(String paymentId) {
    Payment payment = findOrThrow(paymentId);

    // STATE MACHINE GUARD: Only AUTHORIZED payments can be captured
    if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
        throw new InvalidStateTransitionException(
            payment.getStatus().name(), // "VOIDED", "FAILED", etc.
            "capture"
        );
    }

    payment.setStatus(PaymentStatus.CAPTURED);
    paymentRepository.save(payment);
}
```

---

### 4.13 PaymentMethod.java — Supported Payment Types

**File:** `common-lib/src/main/java/com/payflow/common/constant/PaymentMethod.java`

**Purpose:** Defines all payment methods (ways to pay) that PayFlow supports.
Each method has different processing rules, settlement times, and user flows.

**Complete Source Code:**

```java
package com.payflow.common.constant;

/**
 * Payment methods supported by PayFlow.
 */
public enum PaymentMethod {
// Same enum benefits as PaymentStatus: type safety, IDE autocomplete, compiler checks.
// WHY an enum for payment methods?
//   - New payment methods require code changes (intentional — each has different processing)
//   - Prevents typos: "CRD" instead of "CARD" is caught at compile time
//   - Switch statements can handle each method differently:
//     switch(method) { case CARD: processCard(); case UPI: processUpi(); ... }

    /** Credit or Debit card (Visa, Mastercard, RuPay) */
    CARD,
    // HOW IT WORKS:
    //   1. Customer enters: card number, expiry, CVV
    //   2. PayFlow sends to card network (Visa/Mastercard) → issuing bank
    //   3. Bank responds: approve/decline
    //   4. Two-step: AUTHORIZE (hold) then CAPTURE (charge)
    // SETTLEMENT TIME: T+1 to T+3 days (1-3 business days)
    // USE CASE: Online shopping, subscriptions, high-value transactions

    /** UPI (Unified Payments Interface) — India's instant payment */
    UPI,
    // HOW IT WORKS:
    //   1. Customer enters UPI ID (e.g., user@paytm) or scans QR code
    //   2. Payment request sent to customer's UPI app (GPay, PhonePe, Paytm)
    //   3. Customer approves with UPI PIN
    //   4. Money transfers INSTANTLY (real-time settlement)
    // SETTLEMENT TIME: Instant (T+0)
    // USE CASE: India-specific, small to medium transactions, P2P and P2M

    /** Net Banking (redirect to bank website) */
    NETBANKING,
    // HOW IT WORKS:
    //   1. Customer selects their bank from a list
    //   2. Redirected to bank's login page
    //   3. Customer logs in and approves the transaction
    //   4. Bank confirms payment, customer redirected back
    // SETTLEMENT TIME: T+1 to T+2 days
    // USE CASE: Customers who don't have/want to use cards, higher trust

    /** Internal wallet balance */
    WALLET
    // HOW IT WORKS:
    //   1. Customer has pre-loaded money into their PayFlow wallet
    //   2. Payment deducts from wallet balance (instant, internal)
    //   3. No external bank communication needed
    //   4. Fastest processing (purely internal ledger update)
    // SETTLEMENT TIME: Instant (internal transfer)
    // USE CASE: Frequent buyers, loyalty rewards, small transactions
}
```

**How it's used in a payment request:**

```java
// CreatePaymentRequest.java (in payment-service)
public class CreatePaymentRequest {
    @NotNull
    private PaymentMethod paymentMethod;  // CARD, UPI, NETBANKING, or WALLET

    private String cardNumber;      // Only if paymentMethod == CARD
    private String upiId;           // Only if paymentMethod == UPI
    private String bankCode;        // Only if paymentMethod == NETBANKING
}

// Validation in service layer:
if (request.getPaymentMethod() == PaymentMethod.CARD && request.getCardNumber() == null) {
    throw new ValidationException("Card number is required for CARD payments");
}
```

---

### 4.14 Design Patterns Summary

Here's a recap of all design patterns used across the common-lib:

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **Factory Method** | `ApiResponse.success()`, `ApiResponse.error()`, `PagedResponse.of()` | Creates objects without exposing Builder complexity to callers |
| **Builder** | `ApiResponse`, `PagedResponse` (via Lombok `@Builder`) | Readable multi-field object construction without parameter confusion |
| **Template Method** | `PayflowException` base class → subclasses fill in httpStatus | Subclasses define specifics (404, 409, 400); handler works on base type |
| **Centralized Exception Handling** | `GlobalExceptionHandler` | One place to catch ALL errors for ALL controllers |
| **Enum as State Machine** | `PaymentStatus` | Compiler-enforced valid states; no invalid string values possible |
| **Utility Class (static methods)** | `IdGenerator` | Stateless, no instantiation needed, simple to call from anywhere |
| **Inner Static Class** | `PagedResponse.PaginationInfo` | Groups related data without polluting the package with tiny classes |

---

## 5. Verification Steps

After creating all files, verify the project compiles:

```cmd
cd payflow-payment-gateway
mvn clean install -DskipTests -pl common-lib
```

**Expected output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 5.xxx s
```

If it fails, check:
- Java 17 is installed (`java -version`)
- Maven is installed (`mvn -version`)
- You're in the correct directory

---

## 6. Interview Notes

**Q: "What is a multi-module Maven project?"**
> "It's a parent POM with multiple child modules. The parent controls dependency versions and build configuration. Each child is an independent service that inherits from the parent. This way I manage Spring Boot version, Java version, and all library versions in one place."

**Q: "Why a common library?"**
> "To avoid code duplication across 11 services. Shared DTOs (ApiResponse, ErrorDetail), common exceptions, utility classes, and enums live in common-lib. Every service depends on it."

**Q: "How do you handle errors consistently?"**
> "I have a GlobalExceptionHandler using @RestControllerAdvice. It catches all exceptions — business exceptions (PayflowException), validation errors (@Valid failures), and unexpected errors — and converts them to a standard JSON format with error code, message, and HTTP status."

**Q: "Why use Lombok annotations like @Data and @Builder?"**
> "To eliminate boilerplate. @Data generates getters, setters, equals, hashCode, and toString. @Builder generates a fluent builder pattern. This keeps our DTO classes focused on their FIELDS — the business data — not drowned in 100 lines of getter/setter code that adds no value."

**Q: "Explain your ID generation strategy."**
> "I use a custom ID generator with SecureRandom that produces prefixed, 10-character alphanumeric IDs like pay_Hk7mN3xQp2. The prefix tells you the entity type at a glance. With 62^10 possible combinations (839 trillion), collision probability is negligible at our scale. SecureRandom ensures IDs are unpredictable — attackers can't enumerate them."

**Q: "How does the payment state machine work?"**
> "PaymentStatus is a Java enum with 9 states. The flow is: CREATED → PROCESSING → AUTHORIZED → CAPTURED → SETTLED. At each transition, the service checks the current state before allowing the change. Invalid transitions (like capturing a VOIDED payment) throw InvalidStateTransitionException with HTTP 400."

---

## Next Step

→ Continue to **`phase3-part2-service-registry-eureka.md`**

In Part 2, we set up the Eureka Server and verify it starts correctly.
