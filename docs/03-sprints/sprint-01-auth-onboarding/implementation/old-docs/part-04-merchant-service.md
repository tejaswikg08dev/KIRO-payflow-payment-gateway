# Sprint 1, Part 04: Merchant Service

**Duration:** 3-4 hours  
**Prerequisites:** Parts 01-03 completed, Identity Service running

---

## 1. What We're Building

The Merchant Service handles merchant onboarding and profile management:

| Feature | Endpoint | Purpose |
|---------|----------|---------|
| Create Merchant | POST /v1/merchants | Register business |
| Get My Merchant | GET /v1/merchants/me | Get own merchant profile |
| Get Merchant | GET /v1/merchants/{id} | Get merchant by ID |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MERCHANT SERVICE OVERVIEW                                │
│                                                                              │
│  User registers & logs in (Identity Service)                                │
│       │                                                                      │
│       │ Has JWT token                                                        │
│       ▼                                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      MERCHANT SERVICE                                │   │
│  │                        (Port 8082)                                   │   │
│  │                                                                      │   │
│  │  POST /v1/merchants                                                  │   │
│  │  ┌────────────────────────────────────────────────────────────────┐ │   │
│  │  │ 1. Read X-User-Id from header (set by Gateway)                 │ │   │
│  │  │ 2. Check if user already has merchant                          │ │   │
│  │  │ 3. Generate merchant ID (mer_xxxxxxxxxxxx)                     │ │   │
│  │  │ 4. Save merchant to database                                   │ │   │
│  │  │ 5. Return merchant details                                     │ │   │
│  │  └────────────────────────────────────────────────────────────────┘ │   │
│  │                                                                      │   │
│  │                              │                                       │   │
│  │                              ▼                                       │   │
│  │                      ┌───────────────┐                               │   │
│  │                      │  PostgreSQL   │                               │   │
│  │                      │  (merchant    │                               │   │
│  │                      │   schema)     │                               │   │
│  │                      └───────────────┘                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Merchant Onboarding Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MERCHANT ONBOARDING FLOW                                  │
│                                                                              │
│  Step 1: User registers/logs in → Gets JWT token                            │
│                                                                              │
│  Step 2: User creates merchant profile                                       │
│                                                                              │
│  Client                   Gateway                    Merchant Service        │
│    │                         │                            │                  │
│    │ POST /v1/merchants      │                            │                  │
│    │ {businessName, type}    │                            │                  │
│    │ Header: Bearer <token>  │                            │                  │
│    │ ────────────────────────►                            │                  │
│    │                         │                            │                  │
│    │                         │ 1. Validate JWT            │                  │
│    │                         │ 2. Extract user ID         │                  │
│    │                         │ 3. Add X-User-Id header    │                  │
│    │                         │ ──────────────────────────►│                  │
│    │                         │                            │                  │
│    │                         │                            │ 4. Check user    │
│    │                         │                            │    doesn't have  │
│    │                         │                            │    merchant      │
│    │                         │                            │                  │
│    │                         │                            │ 5. Generate ID   │
│    │                         │                            │    mer_abc123... │
│    │                         │                            │                  │
│    │                         │                            │ 6. Save to DB    │
│    │                         │                            │                  │
│    │                         │ ◄──────────────────────────│                  │
│    │ ◄────────────────────────                            │                  │
│    │ {id, businessName, ...} │                            │                  │
│    │                         │                            │                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Merchant ID Generation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MERCHANT ID FORMAT                                        │
│                                                                              │
│  Format: mer_[12 characters]                                                │
│                                                                              │
│  Example: mer_abc123xyz789                                                   │
│           │   │                                                              │
│           │   └── 12 random alphanumeric characters                         │
│           └────── Prefix identifies entity type                             │
│                                                                              │
│  Why this format?                                                            │
│  • Prefix makes IDs self-describing (you know it's a merchant)              │
│  • Similar to Stripe's ID format (cus_, sub_, etc.)                         │
│  • Unique across the system                                                 │
│  • Human-readable (easier than UUIDs)                                       │
│                                                                              │
│  PayFlow ID Prefixes:                                                        │
│  ┌──────────┬─────────────────────────────────────────────────────────────┐│
│  │ Prefix   │ Entity                                                      ││
│  ├──────────┼─────────────────────────────────────────────────────────────┤│
│  │ mer_     │ Merchant                                                    ││
│  │ ord_     │ Order                                                       ││
│  │ pay_     │ Payment                                                     ││
│  │ ref_     │ Refund                                                      ││
│  │ key_     │ API Key                                                     ││
│  │ wh_      │ Webhook                                                     ││
│  │ stl_     │ Settlement                                                  ││
│  └──────────┴─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Merchant Status Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MERCHANT STATUS STATES                                    │
│                                                                              │
│                           ┌──────────────┐                                  │
│                           │   PENDING    │ ← Initial state                  │
│                           │              │   (awaiting verification)        │
│                           └──────┬───────┘                                  │
│                                  │                                           │
│                    ┌─────────────┼─────────────┐                            │
│                    │ Approved    │             │ Rejected                   │
│                    ▼             │             ▼                            │
│             ┌──────────────┐     │      ┌──────────────┐                    │
│             │    ACTIVE    │     │      │   REJECTED   │                    │
│             │              │     │      │              │                    │
│             │ Can accept   │     │      │ Cannot use   │                    │
│             │ payments     │     │      │ platform     │                    │
│             └──────┬───────┘     │      └──────────────┘                    │
│                    │             │                                           │
│                    │ Suspended   │                                           │
│                    ▼             │                                           │
│             ┌──────────────┐     │                                          │
│             │  SUSPENDED   │     │                                          │
│             │              │     │                                          │
│             │ Temporarily  │─────┘ Re-activated                             │
│             │ disabled     │                                                │
│             └──────────────┘                                                │
│                                                                              │
│  For Sprint 1: We auto-set to PENDING (no approval flow yet)                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation

### Step 3.1: Update Parent POM

Add merchant-service module to parent `pom.xml`:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>
    <module>api-gateway</module>
    <module>identity-service</module>
    <module>merchant-service</module>    <!-- ADD THIS -->
</modules>
```

---

### Step 3.2: Create Merchant Service Module

**Create folder structure:**

```powershell
mkdir merchant-service
mkdir merchant-service\src\main\java\com\payflow\merchant
mkdir merchant-service\src\main\java\com\payflow\merchant\controller
mkdir merchant-service\src\main\java\com\payflow\merchant\service
mkdir merchant-service\src\main\java\com\payflow\merchant\service\impl
mkdir merchant-service\src\main\java\com\payflow\merchant\repository
mkdir merchant-service\src\main\java\com\payflow\merchant\entity
mkdir merchant-service\src\main\java\com\payflow\merchant\dto
mkdir merchant-service\src\main\resources
mkdir merchant-service\src\test\java\com\payflow\merchant
```

---

### Step 3.3: Create merchant-service/pom.xml

Create `merchant-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>merchant-service</artifactId>
    <name>PayFlow Merchant Service</name>
    <description>Merchant onboarding and management service</description>

    <dependencies>
        <!-- Spring Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Eureka Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Config Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Common library -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### Step 3.4: Create Merchant Entity

Create `merchant-service/src/main/java/com/payflow/merchant/entity/Merchant.java`:

```java
package com.payflow.merchant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Merchant Entity
 * 
 * Represents a business registered on PayFlow.
 * Each user can have only one merchant account.
 */
@Entity
@Table(
    name = "merchants",
    schema = "merchant",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "user_id", name = "uk_merchants_user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Merchant {

    /**
     * Primary key - Custom format: mer_xxxxxxxxxxxx
     * NOT a UUID - we generate our own format
     */
    @Id
    @Column(name = "id", length = 50, updatable = false, nullable = false)
    private String id;

    /**
     * Reference to user (from identity service)
     * One user can have only one merchant
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * Business name
     */
    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    /**
     * Business type
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 50)
    private BusinessType businessType;

    /**
     * Country code (ISO 3166-1 alpha-2)
     * Examples: US, IN, GB, DE
     */
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    /**
     * Merchant status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING;

    /**
     * Webhook URL for notifications
     */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /**
     * Webhook secret for signing
     */
    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    /**
     * Timestamps
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Business type enum
     */
    public enum BusinessType {
        INDIVIDUAL,  // Solo proprietor
        COMPANY,     // Registered company
        PARTNERSHIP, // Partnership firm
        NON_PROFIT   // Non-profit organization
    }

    /**
     * Merchant status enum
     */
    public enum MerchantStatus {
        PENDING,    // Awaiting verification
        ACTIVE,     // Can accept payments
        SUSPENDED,  // Temporarily disabled
        REJECTED    // Application rejected
    }
}
```


---

### Step 3.5: Create MerchantRepository

Create `merchant-service/src/main/java/com/payflow/merchant/repository/MerchantRepository.java`:

```java
package com.payflow.merchant.repository;

import com.payflow.merchant.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Merchant Repository
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, String> {

    /**
     * Find merchant by user ID
     */
    Optional<Merchant> findByUserId(UUID userId);

    /**
     * Check if user already has a merchant
     */
    boolean existsByUserId(UUID userId);
}
```

---

### Step 3.6: Create IdGenerator Utility

Create `merchant-service/src/main/java/com/payflow/merchant/util/IdGenerator.java`:

```java
package com.payflow.merchant.util;

import java.security.SecureRandom;

/**
 * ID Generator
 * 
 * Generates unique IDs in PayFlow format: prefix_xxxxxxxxxxxx
 * 
 * Example IDs:
 * - mer_abc123xyz789 (merchant)
 * - ord_def456uvw012 (order)
 * - pay_ghi789rst345 (payment)
 */
public class IdGenerator {

    // Alphanumeric characters (no confusing chars like 0/O, 1/l)
    private static final String CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int ID_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate merchant ID: mer_xxxxxxxxxxxx
     */
    public static String merchantId() {
        return generate("mer");
    }

    /**
     * Generate order ID: ord_xxxxxxxxxxxx
     */
    public static String orderId() {
        return generate("ord");
    }

    /**
     * Generate payment ID: pay_xxxxxxxxxxxx
     */
    public static String paymentId() {
        return generate("pay");
    }

    /**
     * Generate refund ID: ref_xxxxxxxxxxxx
     */
    public static String refundId() {
        return generate("ref");
    }

    /**
     * Generate API key ID: key_xxxxxxxxxxxx
     */
    public static String apiKeyId() {
        return generate("key");
    }

    /**
     * Generate webhook ID: wh_xxxxxxxxxxxx
     */
    public static String webhookId() {
        return generate("wh");
    }

    /**
     * Generate ID with given prefix
     */
    public static String generate(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append("_");
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
```

---

### Step 3.7: Create DTOs

Create `merchant-service/src/main/java/com/payflow/merchant/dto/CreateMerchantRequest.java`:

```java
package com.payflow.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Merchant Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMerchantRequest {

    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 255, message = "Business name must be 2-255 characters")
    private String businessName;

    @NotBlank(message = "Business type is required")
    @Pattern(
        regexp = "INDIVIDUAL|COMPANY|PARTNERSHIP|NON_PROFIT",
        message = "Business type must be INDIVIDUAL, COMPANY, PARTNERSHIP, or NON_PROFIT"
    )
    private String businessType;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2, message = "Country must be ISO 3166-1 alpha-2 code (e.g., IN, US)")
    private String country;
}
```

Create `merchant-service/src/main/java/com/payflow/merchant/dto/MerchantResponse.java`:

```java
package com.payflow.merchant.dto;

import com.payflow.merchant.entity.Merchant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Merchant Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {

    private String id;
    private UUID userId;
    private String businessName;
    private String businessType;
    private String country;
    private String status;
    private String webhookUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert entity to DTO
     */
    public static MerchantResponse fromEntity(Merchant merchant) {
        return MerchantResponse.builder()
            .id(merchant.getId())
            .userId(merchant.getUserId())
            .businessName(merchant.getBusinessName())
            .businessType(merchant.getBusinessType().name())
            .country(merchant.getCountry())
            .status(merchant.getStatus().name())
            .webhookUrl(merchant.getWebhookUrl())
            .createdAt(merchant.getCreatedAt())
            .updatedAt(merchant.getUpdatedAt())
            .build();
    }
}
```

---

### Step 3.8: Create MerchantService

Create `merchant-service/src/main/java/com/payflow/merchant/service/MerchantService.java`:

```java
package com.payflow.merchant.service;

import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantResponse;

import java.util.UUID;

/**
 * Merchant Service Interface
 */
public interface MerchantService {

    /**
     * Create a new merchant for user
     */
    MerchantResponse createMerchant(CreateMerchantRequest request, UUID userId);

    /**
     * Get merchant by user ID
     */
    MerchantResponse getMerchantByUserId(UUID userId);

    /**
     * Get merchant by merchant ID
     */
    MerchantResponse getMerchantById(String merchantId);
}
```

Create `merchant-service/src/main/java/com/payflow/merchant/service/impl/MerchantServiceImpl.java`:

```java
package com.payflow.merchant.service.impl;

import com.payflow.common.exception.PayflowException;
import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.entity.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.merchant.service.MerchantService;
import com.payflow.merchant.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Merchant Service Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;

    @Override
    public MerchantResponse createMerchant(CreateMerchantRequest request, UUID userId) {
        log.info("Creating merchant for user: {}", userId);

        // Check if user already has a merchant
        if (merchantRepository.existsByUserId(userId)) {
            log.warn("User already has a merchant: {}", userId);
            throw new PayflowException(
                "MERCHANT_EXISTS",
                "User already has a merchant account",
                HttpStatus.CONFLICT
            );
        }

        // Generate unique merchant ID
        String merchantId = IdGenerator.merchantId();
        
        // Ensure ID is unique (extremely unlikely collision, but check anyway)
        while (merchantRepository.existsById(merchantId)) {
            merchantId = IdGenerator.merchantId();
        }

        // Create merchant entity
        Merchant merchant = Merchant.builder()
            .id(merchantId)
            .userId(userId)
            .businessName(request.getBusinessName().trim())
            .businessType(Merchant.BusinessType.valueOf(request.getBusinessType()))
            .country(request.getCountry().toUpperCase().trim())
            .status(Merchant.MerchantStatus.PENDING)  // Start as pending
            .build();

        merchant = merchantRepository.save(merchant);
        log.info("Merchant created: {} for user: {}", merchant.getId(), userId);

        return MerchantResponse.fromEntity(merchant);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantResponse getMerchantByUserId(UUID userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
            .orElseThrow(() -> new PayflowException(
                "MERCHANT_NOT_FOUND",
                "No merchant found for this user",
                HttpStatus.NOT_FOUND
            ));

        return MerchantResponse.fromEntity(merchant);
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(String merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
            .orElseThrow(() -> new PayflowException(
                "MERCHANT_NOT_FOUND",
                "Merchant not found: " + merchantId,
                HttpStatus.NOT_FOUND
            ));

        return MerchantResponse.fromEntity(merchant);
    }
}
```

---

### Step 3.9: Create MerchantController

Create `merchant-service/src/main/java/com/payflow/merchant/controller/MerchantController.java`:

```java
package com.payflow.merchant.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.merchant.dto.CreateMerchantRequest;
import com.payflow.merchant.dto.MerchantResponse;
import com.payflow.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Merchant Controller
 * 
 * REST API endpoints for merchant management:
 * - POST /v1/merchants      - Create merchant (requires JWT)
 * - GET  /v1/merchants/me   - Get my merchant (requires JWT)
 * - GET  /v1/merchants/{id} - Get merchant by ID (requires JWT)
 */
@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
@Slf4j
public class MerchantController {

    private final MerchantService merchantService;

    /**
     * Create a new merchant
     * 
     * POST /v1/merchants
     * 
     * The X-User-Id header is set by API Gateway after JWT validation.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MerchantResponse>> createMerchant(
            @Valid @RequestBody CreateMerchantRequest request,
            @RequestHeader("X-User-Id") String userIdHeader) {
        
        log.info("POST /v1/merchants - userId: {}", userIdHeader);
        
        UUID userId = UUID.fromString(userIdHeader);
        MerchantResponse response = merchantService.createMerchant(request, userId);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * Get current user's merchant
     * 
     * GET /v1/merchants/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMyMerchant(
            @RequestHeader("X-User-Id") String userIdHeader) {
        
        log.info("GET /v1/merchants/me - userId: {}", userIdHeader);
        
        UUID userId = UUID.fromString(userIdHeader);
        MerchantResponse response = merchantService.getMerchantByUserId(userId);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get merchant by ID
     * 
     * GET /v1/merchants/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMerchantById(
            @PathVariable String id) {
        
        log.info("GET /v1/merchants/{}", id);
        
        MerchantResponse response = merchantService.getMerchantById(id);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

### Step 3.10: Create Application Class

Create `merchant-service/src/main/java/com/payflow/merchant/MerchantServiceApplication.java`:

```java
package com.payflow.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Merchant Service Application
 * 
 * Handles merchant onboarding and management.
 */
@SpringBootApplication
public class MerchantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
```

---

### Step 3.11: Create application.yml

Create `merchant-service/src/main/resources/application.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# Merchant Service Configuration
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8082

spring:
  application:
    name: merchant-service
    
  config:
    import: optional:configserver:http://localhost:8888
    
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow
    username: payflow
    password: payflow123
    driver-class-name: org.postgresql.Driver
    
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: merchant

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info

logging:
  level:
    com.payflow.merchant: DEBUG
```

---

## 4. Verification

### 4.1 Build and Run

```powershell
cd merchant-service
mvn clean package -DskipTests
mvn spring-boot:run
```

### 4.2 Test Merchant Creation

```powershell
# First, login to get JWT token
$loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/v1/auth/login" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"email":"test@example.com","password":"Test1234"}'

$token = $loginResponse.data.accessToken

# Create merchant
Invoke-RestMethod -Uri "http://localhost:8080/v1/merchants" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{ "Authorization" = "Bearer $token" } `
  -Body '{"businessName":"Acme Store","businessType":"COMPANY","country":"IN"}'
```

**Expected response:**
```json
{
  "success": true,
  "data": {
    "id": "mer_abc123xyz789",
    "userId": "550e8400-...",
    "businessName": "Acme Store",
    "businessType": "COMPANY",
    "country": "IN",
    "status": "PENDING",
    "createdAt": "2026-08-04T10:30:00"
  }
}
```

### 4.3 Verification Checklist

| Check | Test | Expected |
|-------|------|----------|
| Service running | /actuator/health | UP |
| Eureka registration | http://localhost:8761 | MERCHANT-SERVICE listed |
| Create merchant | POST /v1/merchants | 201 with merchant data |
| Duplicate merchant | Create again | 409 Conflict |
| Get my merchant | GET /v1/merchants/me | 200 with merchant data |
| Get by ID | GET /v1/merchants/{id} | 200 with merchant data |

---

## 5. File Structure After This Part

```
merchant-service/
├── pom.xml
└── src/main/
    ├── java/com/payflow/merchant/
    │   ├── MerchantServiceApplication.java
    │   ├── controller/
    │   │   └── MerchantController.java
    │   ├── dto/
    │   │   ├── CreateMerchantRequest.java
    │   │   └── MerchantResponse.java
    │   ├── entity/
    │   │   └── Merchant.java
    │   ├── repository/
    │   │   └── MerchantRepository.java
    │   ├── service/
    │   │   ├── MerchantService.java
    │   │   └── impl/
    │   │       └── MerchantServiceImpl.java
    │   └── util/
    │       └── IdGenerator.java
    └── resources/
        └── application.yml
```

---

## 6. Key Takeaways

| Concept | What We Learned |
|---------|-----------------|
| Custom IDs | mer_xxxx format is more readable than UUIDs |
| One-to-One | Each user can have only one merchant |
| Status Flow | Merchants start PENDING, then ACTIVE |
| Header Trust | Services trust X-User-Id from Gateway |

---

## 7. Next Steps

**Merchant Service complete!** You now have:
- ✅ Merchant entity with custom ID format
- ✅ Create merchant API
- ✅ Get merchant APIs
- ✅ User-merchant relationship

**Continue to:** [Part 05: React Frontend](./part-05-react-frontend.md)

In Part 05, you'll build:
- React + TypeScript setup
- Login and Register pages
- Dashboard layout
- Merchant onboarding form

---

**End of Sprint 1, Part 04**

*Next: React Frontend for User Interface*
