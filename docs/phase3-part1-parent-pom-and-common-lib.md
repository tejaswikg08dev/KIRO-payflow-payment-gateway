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

## 4. Common Library Code (File by File)

### 4.1 ApiResponse.java — Standard Response Wrapper

**Purpose:** Every API endpoint returns data in this consistent format.
Consumers (frontend, Postman) always know the structure.

**File:** `common-lib/src/main/java/com/payflow/common/dto/ApiResponse.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;     // true or false
    private T data;              // The actual data (payment, merchant, etc.)
    private ErrorDetail error;   // Only present on errors
    private Instant timestamp;   // When this response was generated
}
```

**Usage in controller:**
```java
// Success
return ResponseEntity.ok(ApiResponse.success(paymentResponse));

// Error
return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "Payment not found"));
```

### 4.2 IdGenerator.java — Short Unique IDs

**Purpose:** Generate readable, short IDs instead of long UUIDs.

**Why not UUID?**
```
UUID:    550e8400-e29b-41d4-a716-446655440000  (36 chars — ugly in URLs and logs)
Our ID:  pay_Hk7mN3xQp2                        (14 chars — clean, prefixed, readable)
```

**How it works:**
- Picks 10 random characters from [A-Z, a-z, 0-9] (62 possibilities per char)
- Prepends a prefix (pay_, ord_, merch_, etc.)
- 62^10 = 839 trillion combinations — collision practically impossible

### 4.3 GlobalExceptionHandler.java — Catches ALL Errors

**Purpose:** When any exception is thrown in any controller, this catches it
and returns a proper JSON error response (not a raw stacktrace).

**Without this handler:**
```
Customer sends invalid request → Server throws exception → 
Customer sees: "Whitelabel Error Page" or raw 500 stacktrace 😱
```

**With this handler:**
```
Customer sends invalid request → Server throws exception →
GlobalExceptionHandler catches it → Returns proper JSON:
{
  "success": false,
  "error": { "code": "VALIDATION_ERROR", "message": "Email is required" }
}
```

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

---

## Next Step

→ Continue to **`phase3-part2-service-registry-eureka.md`**

In Part 2, we set up the Eureka Server and verify it starts correctly.
