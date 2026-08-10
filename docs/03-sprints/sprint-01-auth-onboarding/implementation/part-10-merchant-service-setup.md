# Sprint 1, Part 10: Merchant Service Setup

**Duration:** 1.5-2 hours  
**Prerequisites:** Parts 01-09 completed, Identity Service running

---

## 1. What We're Building

In this part, you'll set up the **Merchant Service** - the service for business onboarding.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MERCHANT SERVICE ROLE                                    │
│                                                                              │
│  The Merchant Service manages business entities that use PayFlow.           │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    MERCHANT LIFECYCLE                                │   │
│  │                                                                      │   │
│  │  1. REGISTRATION                                                    │   │
│  │     Merchant provides business details                              │   │
│  │     └─► Status: PENDING                                             │   │
│  │                                                                      │   │
│  │  2. VERIFICATION (KYC)                                              │   │
│  │     Business documents reviewed                                     │   │
│  │     └─► kyc_verified: true/false                                    │   │
│  │                                                                      │   │
│  │  3. ACTIVE                                                          │   │
│  │     Can receive API keys and process payments                      │   │
│  │     └─► Status: ACTIVE                                             │   │
│  │                                                                      │   │
│  │  4. SUSPENDED (if issues)                                           │   │
│  │     Temporarily blocked from processing                            │   │
│  │     └─► Status: SUSPENDED                                          │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ENDPOINTS:                                                                 │
│  ──────────                                                                 │
│  POST   /v1/merchants        - Register new merchant                       │
│  GET    /v1/merchants/{id}   - Get merchant details                        │
│  PUT    /v1/merchants/{id}   - Update merchant                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 Merchant Entity Relationships

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DATA MODEL                                                │
│                                                                              │
│  ┌─────────────────┐         ┌─────────────────────────┐                   │
│  │      USER       │         │       MERCHANT          │                   │
│  │  (identity.     │         │     (merchant.          │                   │
│  │    users)       │         │      merchants)         │                   │
│  ├─────────────────┤         ├─────────────────────────┤                   │
│  │ id (VARCHAR 50) │◄────────│ user_id (FK)            │                   │
│  │ email           │         │ id (VARCHAR 50)         │                   │
│  │ password_hash   │         │ business_name           │                   │
│  │ full_name       │         │ business_type           │                   │
│  │ role            │         │ registration_number     │                   │
│  └─────────────────┘         │ gst_number              │                   │
│                              │ kyc_verified            │                   │
│                              │ status                  │                   │
│                              │ ...                     │                   │
│                              └───────────┬─────────────┘                   │
│                                          │                                  │
│                                          │ 1:N                             │
│                                          ▼                                  │
│                              ┌─────────────────────────┐                   │
│                              │       API_KEYS          │                   │
│                              │  (merchant.api_keys)    │                   │
│                              ├─────────────────────────┤                   │
│                              │ merchant_id (FK)        │                   │
│                              │ public_key              │                   │
│                              │ secret_key_hash         │                   │
│                              │ key_type                │                   │
│                              └─────────────────────────┘                   │
│                                                                              │
│  Note: Both tables are in the SAME database (payflow)                      │
│  but in DIFFERENT schemas (identity vs merchant).                          │
│  user_id is a soft reference - no actual FK constraint.                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Schema Separation Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SCHEMA PER SERVICE                                        │
│                                                                              │
│  PayFlow uses ONE database with MULTIPLE schemas:                           │
│                                                                              │
│  payflow (database)                                                         │
│  ├── identity (schema)     ← Identity Service                              │
│  │   └── users                                                             │
│  ├── merchant (schema)     ← Merchant Service                              │
│  │   ├── merchants                                                         │
│  │   └── api_keys                                                          │
│  └── payment (schema)      ← Payment Service                               │
│      ├── payments                                                          │
│      └── transactions                                                      │
│                                                                              │
│  Benefits:                                                                   │
│  • Each service owns its schema                                            │
│  • Can evolve independently                                                │
│  • Simpler ops than separate databases                                     │
│  • Easy to query across schemas if needed                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

```powershell
# Services should be running
curl http://localhost:8761/actuator/health  # Eureka
curl http://localhost:8080/actuator/health  # API Gateway
curl http://localhost:8081/actuator/health  # Identity Service
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Create Folder Structure

```powershell
mkdir merchant-service
mkdir merchant-service\src\main\java\com\payflow\merchant
mkdir merchant-service\src\main\java\com\payflow\merchant\controller
mkdir merchant-service\src\main\java\com\payflow\merchant\model
mkdir merchant-service\src\main\java\com\payflow\merchant\repository
mkdir merchant-service\src\main\java\com\payflow\merchant\service
mkdir merchant-service\src\main\resources
mkdir merchant-service\src\main\resources\db\migration
mkdir merchant-service\src\test\java\com\payflow\merchant
```

**Important:** The entity package is named `model` (not `entity`), matching our convention.

---

### Step 4.2: Create pom.xml

**File: `merchant-service/pom.xml`**

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
    <description>Merchant onboarding, API key management, and fee configuration</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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

**Note:** Lombok is inherited from parent POM. No test dependencies needed here (also inherited).

---

### Step 4.3: Create Main Application Class

**File: `merchant-service/src/main/java/com/payflow/merchant/MerchantServiceApplication.java`**

```java
package com.payflow.merchant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.merchant", "com.payflow.common"})
@OpenAPIDefinition(info = @Info(
        title = "PayFlow Merchant Service API",
        version = "1.0",
        description = "Merchant onboarding, API key management, and fee configuration"
))
public class MerchantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
```

**Key annotations:**
- `@ComponentScan` - Includes `com.payflow.common` to pick up shared components (GlobalExceptionHandler, ApiResponse, etc.)
- `@OpenAPIDefinition` - Configures Swagger documentation metadata

---

### Step 4.4: Create application.yml

**File: `merchant-service/src/main/resources/application.yml`**

```yaml
server:
  port: 8082

spring:
  application:
    name: merchant-service
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=merchant
    username: payflow
    password: payflow_secret
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        default_schema: merchant
  flyway:
    enabled: true
    schemas: merchant
    locations: classpath:db/migration

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

**Important differences from Identity Service:**
- Uses `?currentSchema=merchant` in JDBC URL
- Sets `hibernate.default_schema: merchant`
- Flyway creates migrations in `merchant` schema
- No config server import (simplified for now)

---

### Step 4.5: Create Schema in Database

The merchant schema will be created automatically by Flyway. But if you want to create it manually:

```powershell
# Connect to PostgreSQL and create schema
docker exec -it payflow-postgres psql -U payflow -d payflow -c "CREATE SCHEMA IF NOT EXISTS merchant;"
```

---

## 5. Verification

### 5.1 Build the Module

```powershell
cd merchant-service
mvn clean package -DskipTests
```

### 5.2 Run (will fail without migrations - that's expected)

```powershell
mvn spring-boot:run
# Will show Flyway error - that's OK, we'll add migrations in Part 11
```

---

## 6. File Structure

After completing this part:

```
merchant-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/payflow/merchant/
│   │   │   ├── MerchantServiceApplication.java
│   │   │   ├── controller/      (empty for now)
│   │   │   ├── model/           (empty for now)
│   │   │   ├── repository/      (empty for now)
│   │   │   └── service/         (empty for now)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    (empty for now)
│   └── test/
│       └── java/com/payflow/merchant/
```

---

## 7. Key Takeaways

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHAT YOU LEARNED                                          │
│                                                                              │
│  ✅ Schema Per Service Pattern                                              │
│     • Same database, different schemas                                     │
│     • identity schema, merchant schema, payment schema                     │
│     • Each service owns and manages its own schema                         │
│                                                                              │
│  ✅ Entity Package Convention                                              │
│     • We use `model` package (not `entity`)                               │
│     • Consistent across all services                                       │
│                                                                              │
│  ✅ ComponentScan for Common Library                                       │
│     • @ComponentScan includes "com.payflow.common"                        │
│     • Picks up GlobalExceptionHandler, ApiResponse, etc.                  │
│                                                                              │
│  ✅ Flyway Schema Configuration                                            │
│     • flyway.schemas: merchant                                             │
│     • Creates migrations in the merchant schema                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Q&A / Troubleshooting

### Q1: Service doesn't register with Eureka

**Check:**
1. Eureka is running at `localhost:8761`
2. `spring-cloud-starter-netflix-eureka-client` is in pom.xml
3. `eureka.client.service-url.defaultZone` is correct

### Q2: Database connection refused

**Fix:** Ensure PostgreSQL is running:
```powershell
docker ps | grep postgres
# If not running:
docker-compose -f docker-compose-infra.yml up -d postgres
```

### Q3: Schema 'merchant' does not exist

**Fix:** Flyway will create it, or create manually:
```powershell
docker exec -it payflow-postgres psql -U payflow -d payflow -c "CREATE SCHEMA merchant;"
```

---

## 9. Related Concepts

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Schema Isolation vs Database Isolation                                     │
│  ─────────────────────────────────────                                      │
│                                                                              │
│  Schema Isolation (what we use):                                           │
│  • One database, multiple schemas                                          │
│  • Simpler operations                                                      │
│  • Can JOIN across schemas if needed                                       │
│  • Single connection pool                                                  │
│                                                                              │
│  Database Isolation:                                                        │
│  • Separate databases per service                                          │
│  • Stronger isolation                                                      │
│  • More complex operations                                                 │
│  • Cannot JOIN across databases                                            │
│                                                                              │
│  For PayFlow, schema isolation is a good balance of                        │
│  isolation and operational simplicity.                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ✅ Part 10 COMPLETE: Merchant Service Setup                                │
│                                                                              │
│  NEXT: Part 11 - Merchant Database                                          │
│  ─────────────────────────────────                                          │
│  Create entities, Flyway migrations, and repositories.                     │
│                                                                              │
│  Continue to: part-11-merchant-database.md                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Part 10 Complete!** 🎉
