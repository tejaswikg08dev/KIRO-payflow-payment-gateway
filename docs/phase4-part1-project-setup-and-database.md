# Hands-On Guide — Phase 4 Part 1: Identity Service — Project Setup & Database

## Goal

By the end of Part 1, you will have:
- identity-service Maven module created with all dependencies
- Flyway migration that creates the `users` table in PostgreSQL
- User JPA entity class mapped to the table
- application.yml configured for local development
- Service starts up and connects to PostgreSQL
- Your first Git commit for this service

## Prerequisites

- Phase 3 completed (service-registry, config-server, api-gateway all compile)
- Docker infrastructure running: `docker compose -f docker-compose-infra.yml up -d`
- PostgreSQL accessible at localhost:5432 with `payflow` database and `identity` schema

---

## How the Identity Service Fits in the Architecture

```
Customer/Merchant                API Gateway (8080)             Identity Service (8081)
      │                               │                               │
      │  POST /v1/auth/register       │                               │
      │──────────────────────────────►│                               │
      │                               │  Route: /v1/auth/** →         │
      │                               │  lb://IDENTITY-SERVICE        │
      │                               │──────────────────────────────►│
      │                               │                               │ Save user to DB
      │                               │                               │ Generate JWT
      │                               │  {accessToken, refreshToken}  │
      │                               │◄──────────────────────────────│
      │  200 {tokens + user info}     │                               │
      │◄──────────────────────────────│                               │
```

---

## Step 1.1: Create the pom.xml

**What is this?** The Maven build file that declares all dependencies (libraries) this service needs.

**Create file:** `identity-service/pom.xml`

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
        <!-- This service inherits from our parent POM -->
        <!-- Gets Java 17, Spring Boot 3.2.5, all version management for free -->
    </parent>

    <artifactId>identity-service</artifactId>
    <!-- Unique name for this module -->

    <name>PayFlow Identity Service</name>
    <description>User registration, login, JWT authentication</description>

    <dependencies>
        <!-- ===== Spring Boot Starters ===== -->
        
        <!-- spring-boot-starter-web: Gives us REST controllers, embedded Tomcat -->
        <!-- Without this, we can't create @RestController or handle HTTP requests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- spring-boot-starter-security: Authentication & authorization framework -->
        <!-- Provides: password encoding, security filters, CORS, CSRF protection -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- spring-boot-starter-data-jpa: ORM framework (talk to database with Java objects) -->
        <!-- Includes Hibernate (JPA implementation) + Spring Data (repository pattern) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- spring-boot-starter-validation: Bean validation (@NotNull, @Email, @Size) -->
        <!-- Automatically validates request bodies before they reach your code -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- ===== Database ===== -->
        
        <!-- PostgreSQL JDBC driver: Allows Java to connect to PostgreSQL -->
        <!-- runtime scope = needed only when running, not when compiling -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Flyway: Database migration tool (version control for your DB schema) -->
        <!-- On startup, runs SQL scripts in order: V1__, V2__, V3__... -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <!-- ===== JWT (JSON Web Token) ===== -->
        
        <!-- jjwt-api: The API to create and parse JWT tokens -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.5</version>
        </dependency>
        <!-- jjwt-impl: The implementation (does the actual work) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <!-- jjwt-jackson: JSON serialization for JWT (uses Jackson) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>

        <!-- ===== Spring Cloud ===== -->
        
        <!-- Eureka Client: Registers this service with Eureka (service discovery) -->
        <!-- On startup: "Hey Eureka, I'm IDENTITY-SERVICE at localhost:8081" -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- ===== API Documentation ===== -->
        
        <!-- SpringDoc: Auto-generates Swagger UI from your controller annotations -->
        <!-- Access at: http://localhost:8081/swagger-ui.html -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- ===== Our Common Library ===== -->
        
        <!-- Shared DTOs (ApiResponse), exceptions, utilities (IdGenerator) -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>

        <!-- ===== Monitoring ===== -->
        
        <!-- Actuator: Health checks, metrics endpoints (/actuator/health) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Spring Boot Maven Plugin: Creates runnable JAR with embedded Tomcat -->
            <!-- Run with: mvn spring-boot:run -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Step 1.2: Create the Main Application Class

**What is this?** The entry point that starts the Spring Boot application.

**Create file:** `identity-service/src/main/java/com/payflow/identity/IdentityServiceApplication.java`

```java
package com.payflow.identity;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
// @SpringBootApplication = 3 annotations combined:
//   @Configuration: This class can define beans
//   @EnableAutoConfiguration: Spring auto-configures based on dependencies
//   @ComponentScan: Scans this package + sub-packages for @Service, @Controller, etc.

@ComponentScan(basePackages = {"com.payflow.identity", "com.payflow.common"})
// Scan both our package AND the common-lib package
// This picks up GlobalExceptionHandler from common-lib

@OpenAPIDefinition(
        info = @Info(
                title = "PayFlow Identity Service API",
                version = "1.0",
                description = "User registration, login, and JWT token management",
                contact = @Contact(name = "PayFlow Team")
        )
)
// @OpenAPIDefinition: Configures the Swagger UI page title and description
// Visit http://localhost:8081/swagger-ui.html to see it

public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
        // SpringApplication.run() starts:
        //   1. Embedded Tomcat web server
        //   2. Connects to PostgreSQL
        //   3. Runs Flyway migrations
        //   4. Registers with Eureka
        //   5. Starts listening on port 8081
    }
}
```

---

## Step 1.3: Create the Flyway Migration (Database Table)

**What is Flyway?** A tool that runs SQL scripts in order to create/modify your database.

**How it works:**
```
On startup, Flyway:
1. Checks: Does flyway_schema_history table exist? If not, creates it.
2. Reads all files in: src/main/resources/db/migration/
3. Checks which ones already ran (tracked in flyway_schema_history)
4. Runs ONLY new ones that haven't run yet
5. Records them in flyway_schema_history

File naming rule: V{version}__{description}.sql
  V1__create_users_table.sql     → Version 1 (runs first)
  V2__add_phone_column.sql       → Version 2 (runs second)
  
Double underscore (__) between version and description is REQUIRED.
```

**Create file:** `identity-service/src/main/resources/db/migration/V1__create_users_table.sql`

```sql
-- V1: Create the users table for the identity service
-- This runs automatically on first startup via Flyway
-- It ONLY runs in the 'identity' schema (configured in application.yml)

CREATE TABLE IF NOT EXISTS identity.users (
    -- id: Primary key, using our custom short IDs (usr_Hk7mN3xQp2)
    -- VARCHAR(50) because our IDs are about 14 characters
    id              VARCHAR(50) PRIMARY KEY,

    -- email: Must be unique (can't register twice with same email)
    -- Used for login
    email           VARCHAR(255) NOT NULL UNIQUE,

    -- password_hash: BCrypt hashed password (always 60 characters)
    -- We NEVER store plain text passwords
    password_hash   VARCHAR(255) NOT NULL,

    -- full_name: User's display name
    full_name       VARCHAR(100) NOT NULL,

    -- phone: Optional phone number
    phone           VARCHAR(20),

    -- role: CUSTOMER, MERCHANT, or ADMIN
    -- Determines what the user can do
    role            VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',

    -- email_verified: Has user confirmed their email?
    -- Future: Send verification email on registration
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,

    -- status: ACTIVE (normal), SUSPENDED (blocked), DELETED (soft delete)
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- last_login_at: Updated every time user logs in
    -- Useful for: "inactive users" reports, security alerts
    last_login_at   TIMESTAMP,

    -- Audit timestamps
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for fast lookups:

-- idx_users_email: Used every time someone logs in (find user by email)
-- Without this: full table scan every login = SLOW
CREATE INDEX idx_users_email ON identity.users(email);

-- idx_users_status: Used to filter active/suspended users
CREATE INDEX idx_users_status ON identity.users(status);
```

---

## Step 1.4: Create the User Entity (JPA)

**What is a JPA Entity?** A Java class that maps to a database table. Each field = one column.

**Create file:** `identity-service/src/main/java/com/payflow/identity/model/User.java`

```java
package com.payflow.identity.model;

import jakarta.persistence.*;
// jakarta.persistence = JPA annotations (Entity, Table, Column, Id, etc.)
// These tell Hibernate how to map this class to a database table

import lombok.*;
// Lombok generates boilerplate code at compile time:
// @Data = getters + setters + equals + hashCode + toString
// @Builder = Builder pattern (User.builder().email("x").build())
// @NoArgsConstructor = empty constructor (JPA requires this)
// @AllArgsConstructor = constructor with all fields

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
// Auto-set timestamps when entity is created/updated

import java.time.Instant;

@Entity
// @Entity = "This class represents a database table"
// Hibernate will manage instances of this class

@Table(name = "users", schema = "identity")
// @Table: Specifies which table and schema this maps to
// Without this, Hibernate would look for a table named "user" (class name)

@Data
// @Data = @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
// Saves us writing ~50 lines of boilerplate code

@Builder
// @Builder: Enables User.builder().email("x@test.com").fullName("Test").build()
// Much cleaner than new User() followed by 10 setters

@NoArgsConstructor
// Required by JPA/Hibernate (it creates instances via reflection using no-arg constructor)

@AllArgsConstructor
// Needed for @Builder to work (Builder needs a constructor with all fields)

public class User {

    @Id
    // @Id = "This field is the primary key"
    @Column(length = 50)
    // @Column: customizes the column (VARCHAR(50) in this case)
    private String id;
    // We use our custom IDs (usr_Hk7mN3xQp2) instead of auto-generated Long
    // Why? Because UUIDs are too long and sequential IDs are guessable

    @Column(nullable = false, unique = true)
    // nullable = false → NOT NULL constraint in DB
    // unique = true → UNIQUE constraint (can't have duplicate emails)
    private String email;

    @Column(name = "password_hash", nullable = false)
    // name = "password_hash" → maps to this specific column name
    // In Java the field is "passwordHash" (camelCase) but DB uses "password_hash" (snake_case)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    // @Enumerated(STRING): Store the enum NAME as text in DB ("MERCHANT" not 1)
    // Without this, Hibernate stores ordinal (0, 1, 2) which breaks if you reorder enum
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    // @Builder.Default: When using Builder, this field defaults to false
    // Without it, Builder would set it to null (not false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    // @CreationTimestamp: Hibernate automatically sets this to NOW() on INSERT
    // You never need to set it manually
    @Column(name = "created_at", nullable = false, updatable = false)
    // updatable = false → this column never changes after initial creation
    private Instant createdAt;

    @UpdateTimestamp
    // @UpdateTimestamp: Hibernate automatically updates this to NOW() on every UPDATE
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // === Enums defined inside the entity class ===

    public enum Role {
        CUSTOMER,  // End user making payments
        MERCHANT,  // Business accepting payments
        ADMIN      // Platform administrator
    }

    public enum UserStatus {
        ACTIVE,     // Normal account
        SUSPENDED,  // Blocked by admin
        DELETED     // Soft-deleted (data preserved but can't login)
    }
}
```

---

## Step 1.5: Create application.yml

**What is this?** Configuration for local development (port, database URL, JWT secret, etc.)

**Create file:** `identity-service/src/main/resources/application.yml`

```yaml
# ===== Server Configuration =====
server:
  port: 8081
  # This service runs on port 8081
  # API Gateway routes /v1/auth/** here

# ===== Spring Configuration =====
spring:
  application:
    name: identity-service
    # IMPORTANT: This name is used by:
    # 1. Eureka (registers as "IDENTITY-SERVICE")
    # 2. Config Server (looks for identity-service.yml)
    # 3. Other services (find us by this name)

  # ----- Database -----
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=identity
    # Connection URL breakdown:
    # jdbc:postgresql:// → JDBC driver for PostgreSQL
    # localhost:5432     → Docker PostgreSQL running locally
    # /payflow           → Database name (created by docker init-db.sql)
    # ?currentSchema=identity → Use the 'identity' schema
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver

  # ----- JPA / Hibernate -----
  jpa:
    hibernate:
      ddl-auto: validate
      # validate = Hibernate checks that entities match DB tables
      # It does NOT create/modify tables (Flyway does that)
      # Options: none, validate, update, create, create-drop
      # NEVER use 'update' or 'create' in production!
    show-sql: true
    # show-sql: true → prints SQL statements in console (helpful for debugging)
    # Set to false in production (too noisy)
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: identity
        format_sql: true
        # format_sql: true → prints SQL nicely formatted (not one long line)

  # ----- Flyway (Database Migrations) -----
  flyway:
    enabled: true
    schemas: identity
    # Run migrations ONLY in the identity schema
    locations: classpath:db/migration
    # Look for SQL files in: src/main/resources/db/migration/

# ===== JWT Configuration =====
jwt:
  secret: payflow-jwt-secret-key-change-in-production-minimum-256-bits-long-key-here
  # HMAC-SHA256 requires at least 32 bytes (256 bits)
  # In production: use a random 64+ character string stored in AWS Secrets Manager
  access-token-expiry: 900000
  # 900000 ms = 15 minutes
  refresh-token-expiry: 604800000
  # 604800000 ms = 7 days

# ===== Eureka (Service Discovery) =====
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
      # Where to find Eureka server
  instance:
    prefer-ip-address: true
    # Register with IP address instead of hostname
    # Necessary for Docker/container environments

# ===== Swagger / OpenAPI =====
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    # Access Swagger at: http://localhost:8081/swagger-ui.html
  api-docs:
    path: /v3/api-docs
    # OpenAPI JSON spec at: http://localhost:8081/v3/api-docs
```

---

## Step 1.6: Verify It Works

### 1. Make sure Docker infrastructure is running:
```cmd
docker compose -f docker-compose-infra.yml up -d
docker ps
```
You should see `payflow-postgres` running.

### 2. Build the module:
```cmd
cd payflow-payment-gateway
mvn clean install -DskipTests -pl common-lib,identity-service -am
```
Expected: `BUILD SUCCESS`

### 3. Start the service:
```cmd
cd identity-service
mvn spring-boot:run
```

### 4. Check the console output for:
```
Flyway: Migrating schema "identity" to version "1 - create users table"
Started IdentityServiceApplication in 5.xxx seconds
```

### 5. Verify Swagger UI:
Open browser → http://localhost:8081/swagger-ui.html
You should see the Swagger page (empty for now — no controllers yet).

### 6. Verify database table was created:
```cmd
docker exec -it payflow-postgres psql -U payflow -d payflow -c "SELECT * FROM identity.users;"
```
Expected: empty table (0 rows), but table exists!

---

## Step 1.7: Git Commit

```cmd
git add identity-service/
git commit -m "Phase 4 Part 1: Identity service - project setup, entity, flyway migration"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `identity-service/pom.xml` | Dependencies (web, security, JPA, JWT, Eureka, Swagger) |
| `IdentityServiceApplication.java` | Main class (starts the app) |
| `model/User.java` | JPA entity (maps to users table) |
| `resources/application.yml` | Configuration (port, DB, JWT, Eureka) |
| `resources/db/migration/V1__create_users_table.sql` | Database table creation |

---

## Next Step

→ Continue to **Phase 4 Part 2: JWT Service & Authentication Logic**

In Part 2, we'll create:
- JwtService (generate and validate tokens)
- AuthService (register, login business logic)
- RegisterRequest, LoginRequest, AuthResponse DTOs
