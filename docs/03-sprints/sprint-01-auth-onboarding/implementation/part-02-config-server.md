# Sprint 1, Part 02: Config Server

**Duration:** 1.5-2 hours  
**Prerequisites:** Part 01 completed, Service Registry running on port 8761

---

## 1. What We're Building

In this part, you'll build the **Config Server** - the central configuration management system.

| Component | Port | Purpose |
|-----------|------|---------|
| config-server | 8888 | Centralized configuration for all services |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CONFIG SERVER ROLE                                       │
│                                                                              │
│  Without Config Server:               With Config Server:                    │
│  ──────────────────────               ────────────────────                   │
│                                                                              │
│  identity-service/                    config-server/config/                  │
│  └── application.yml                  ├── application.yml    ← SHARED       │
│      db.url: localhost:5432           │   db.url: localhost:5432             │
│      db.user: payflow                 │   db.user: payflow                   │
│      jwt.secret: xxx                  │                                      │
│                                       ├── identity-service.yml ← SPECIFIC   │
│  merchant-service/                    │   jwt.expiry: 86400                  │
│  └── application.yml                  │                                      │
│      db.url: localhost:5432 ← DUP     └── merchant-service.yml ← SPECIFIC   │
│      db.user: payflow       ← DUP         fee.rate: 2.5                     │
│                                                                              │
│  payment-service/                     Services fetch config on startup:      │
│  └── application.yml                                                         │
│      db.url: localhost:5432 ← DUP     identity-service:                      │
│      db.user: payflow       ← DUP     GET /identity-service/default          │
│                                       Returns: application.yml MERGED with   │
│  ❌ 10 services = 10 copies                   identity-service.yml           │
│  ❌ Change DB = edit 10 files                                                │
│  ❌ Easy to miss one                  ✅ Single source of truth              │
│                                       ✅ Change once = all services get it  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 How Config Server Works

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIG SERVER ARCHITECTURE                                │
│                                                                              │
│  CONFIG SOURCE (where configs are stored):                                   │
│  ─────────────────────────────────────────                                  │
│                                                                              │
│  Option 1: Local Files (Native)     ← We'll use this for development        │
│  config-server/src/main/resources/configurations/                           │
│  ├── identity-service.yml     (identity-specific)                           │
│  ├── merchant-service.yml     (merchant-specific)                           │
│  └── payment-service.yml      (payment-specific)                            │
│  └── api-gateway.yml          (gateway-specific)                            │
│                                                                              │
│  Option 2: Git Repository           ← Common for production                 │
│  https://github.com/company/config-repo                                      │
│  ├── application.yml                                                        │
│  ├── identity-service.yml                                                   │
│  └── ...                                                                    │
│  Benefits: Version control, audit trail, PR reviews for config changes      │
│                                                                              │
│  Option 3: HashiCorp Vault          ← For secrets                           │
│  Encrypted storage for passwords, API keys, certificates                    │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CONFIG RETRIEVAL FLOW:                                                      │
│  ──────────────────────                                                     │
│                                                                              │
│  1. Service starts (e.g., identity-service)                                 │
│                                                                              │
│  2. Before loading local config, it asks Config Server:                     │
│     GET http://config-server:8888/identity-service/default                  │
│                                                                              │
│  3. Config Server:                                                           │
│     a. Reads application.yml (shared config)                                │
│     b. Reads identity-service.yml (specific config)                         │
│     c. MERGES them (specific overrides shared)                              │
│     d. Returns combined config as JSON                                       │
│                                                                              │
│  4. identity-service uses the received config                               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Config Precedence (Priority Order)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIGURATION PRECEDENCE                                  │
│                                                                              │
│  When the same property exists in multiple places,                           │
│  which one wins? (Highest priority → Lowest priority)                        │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │  1. Command line arguments                    HIGHEST PRIORITY      │    │
│  │     java -jar app.jar --server.port=9000                           │    │
│  ├────────────────────────────────────────────────────────────────────┤    │
│  │  2. Environment variables                                          │    │
│  │     SPRING_DATASOURCE_URL=jdbc:postgresql://prod:5432              │    │
│  ├────────────────────────────────────────────────────────────────────┤    │
│  │  3. Config Server - Service-specific file                          │    │
│  │     identity-service.yml                                           │    │
│  ├────────────────────────────────────────────────────────────────────┤    │
│  │  4. Config Server - Shared file                                    │    │
│  │     application.yml                                                │    │
│  ├────────────────────────────────────────────────────────────────────┤    │
│  │  5. Local application.yml (in service's resources)                 │    │
│  │     identity-service/src/main/resources/application.yml            │    │
│  ├────────────────────────────────────────────────────────────────────┤    │
│  │  6. Default values                            LOWEST PRIORITY       │    │
│  │     Built into Spring Boot                                         │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  EXAMPLE:                                                                    │
│  ────────                                                                   │
│  Config Server application.yml:    server.port: 8080                        │
│  Config Server identity-service.yml: server.port: 8081                      │
│  Local application.yml:            server.port: 8082                        │
│  Environment variable:             SERVER_PORT=8083                         │
│                                                                              │
│  Result: identity-service runs on port 8083 (env var wins)                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Profiles

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SPRING PROFILES                                           │
│                                                                              │
│  Profiles allow different configurations for different environments.         │
│                                                                              │
│  Config Server files:                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  application.yml              ← Default profile (always loaded)     │   │
│  │  application-dev.yml          ← Development profile                 │   │
│  │  application-prod.yml         ← Production profile                  │   │
│  │  identity-service.yml         ← Default for identity                │   │
│  │  identity-service-dev.yml     ← Development for identity            │   │
│  │  identity-service-prod.yml    ← Production for identity             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  How profiles are activated:                                                 │
│                                                                              │
│  1. Environment variable:                                                    │
│     SPRING_PROFILES_ACTIVE=prod                                             │
│                                                                              │
│  2. Command line:                                                            │
│     java -jar app.jar --spring.profiles.active=prod                         │
│                                                                              │
│  3. In application.yml:                                                      │
│     spring:                                                                  │
│       profiles:                                                              │
│         active: dev                                                          │
│                                                                              │
│  Config Server URL patterns:                                                 │
│  ───────────────────────────                                                │
│  GET /{application}/{profile}                                               │
│                                                                              │
│  /identity-service/default  → application.yml + identity-service.yml        │
│  /identity-service/dev      → above + application-dev.yml                   │
│                               + identity-service-dev.yml                    │
│  /identity-service/prod     → above + application-prod.yml                  │
│                               + identity-service-prod.yml                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Before starting, verify:

```powershell
# Service Registry should be running
curl http://localhost:8761/actuator/health
# Expected: {"status":"UP"}

# Check project root
cd C:\path\to\payflow-payment-gateway
dir
# Should see: pom.xml, common-lib, service-registry
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

**File: `pom.xml` (project root)**

Add config-server to modules:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>
    <module>config-server</module>  <!-- ADD THIS LINE -->
</modules>
```

---

### Step 4.2: Create Folder Structure

```powershell
# Create the config-server module structure
mkdir config-server
mkdir config-server\src\main\java\com\payflow\config
mkdir config-server\src\main\resources
mkdir config-server\src\main\resources\config

# Verify structure
tree config-server /F
```

Expected:
```
config-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── payflow/
│       │           └── config/
│       └── resources/
│           └── config/        ← Configuration files go here
└── pom.xml
```

---

### Step 4.3: Create pom.xml

**File: `config-server/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ═══════════════════════════════════════════════════════════════════
         PARENT REFERENCE
    ═══════════════════════════════════════════════════════════════════ -->
    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <!-- ═══════════════════════════════════════════════════════════════════
         MODULE IDENTITY
    ═══════════════════════════════════════════════════════════════════ -->
    <artifactId>config-server</artifactId>
    <name>PayFlow Config Server</name>
    <description>Spring Cloud Config Server for centralized configuration</description>

    <!-- ═══════════════════════════════════════════════════════════════════
         DEPENDENCIES
    ═══════════════════════════════════════════════════════════════════ -->
    <dependencies>
        
        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ SPRING CLOUD CONFIG SERVER                                      │
        │                                                                  │
        │ What it provides:                                                │
        │ • Config Server implementation                                   │
        │ • REST API: /{application}/{profile}/{label}                    │
        │ • Support for file-based, Git, Vault backends                   │
        │ • Property encryption/decryption                                │
        │                                                                  │
        │ Endpoints exposed:                                               │
        │ • GET /application/default                                       │
        │ • GET /identity-service/dev                                      │
        │ • GET /merchant-service/prod                                     │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>

        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ EUREKA CLIENT                                                   │
        │                                                                  │
        │ What it provides:                                                │
        │ • Registers this service with Eureka                            │
        │ • Sends heartbeats every 30 seconds                             │
        │ • Enables service discovery for this service                    │
        │                                                                  │
        │ Why we need it:                                                  │
        │ • Other services discover Config Server via Eureka              │
        │ • No hardcoded URL needed: bootstrap.yml just says              │
        │   "find config-server via Eureka"                               │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

    </dependencies>

    <!-- ═══════════════════════════════════════════════════════════════════
         BUILD CONFIGURATION
    ═══════════════════════════════════════════════════════════════════ -->
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

### Step 4.4: Create Main Application Class

**File: `config-server/src/main/java/com/payflow/config/ConfigServerApplication.java`**

```java
package com.payflow.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server — Centralized configuration management.
 * 
 * What this does:
 * - Stores configuration (YAML) for ALL services in one place
 * - Each service fetches its config from here on startup
 * - Change config in one place → all services get updated
 * 
 * How services get their config:
 * 1. identity-service starts
 * 2. It calls: http://config-server:8888/identity-service/default
 * 3. Config Server returns identity-service.yml content
 * 4. identity-service uses those settings (DB URL, port, secrets, etc.)
 * 
 * Config files location: config-server/configurations/ folder
 * Each service has its own YAML: identity-service.yml, payment-service.yml, etc.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

---

### Step 4.5: Create Config Server application.yml

**File: `config-server/src/main/resources/application.yml`**

```yaml
# Config Server Configuration
# Port: 8888 (standard Spring Cloud Config port)

server:
  port: 8888

spring:
  application:
    name: config-server
  profiles:
    active: native  # Read config files from local filesystem (not Git)
  cloud:
    config:
      server:
        native:
          # Location of config files for each service
          search-locations: classpath:/configurations

# Register with Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

**Line-by-line explanation:**

| Property | Value | Purpose |
|----------|-------|---------|
| `server.port` | `8888` | Standard Config Server port |
| `spring.application.name` | `config-server` | Service name for Eureka registration |
| `spring.profiles.active` | `native` | Use local files instead of Git |
| `search-locations` | `classpath:/configurations` | Folder containing service configs |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka/` | Eureka Server URL |
| `eureka.instance.prefer-ip-address` | `true` | Register with IP instead of hostname |

---

### Step 4.6: Understanding Config Server Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIG SERVER ARCHITECTURE                                │
│                                                                              │
│  In our PayFlow system, each service has its own COMPLETE configuration     │
│  file in the Config Server. This approach has benefits:                     │
│                                                                              │
│  ✅ Each service's config is self-contained and explicit                    │
│  ✅ No hidden inheritance or merging to debug                               │
│  ✅ Easy to see exactly what config each service gets                       │
│  ✅ Services can have completely different database schemas                 │
│                                                                              │
│  FOLDER STRUCTURE:                                                           │
│  ─────────────────                                                          │
│  config-server/                                                              │
│  └── src/main/resources/                                                     │
│      ├── application.yml           ← Config Server's OWN config             │
│      │   (port 8888, Eureka settings, native profile)                       │
│      │                                                                       │
│      └── configurations/           ← Configs SERVED TO other services       │
│          ├── identity-service.yml  ← Identity gets its own complete config  │
│          ├── merchant-service.yml  ← Merchant gets its own complete config  │
│          └── payment-service.yml   ← Payment gets its own complete config   │
│                                                                              │
│  CONFIG RETRIEVAL:                                                           │
│  ─────────────────                                                          │
│  When identity-service starts:                                               │
│  1. Calls GET http://config-server:8888/identity-service/default            │
│  2. Config Server reads configurations/identity-service.yml                 │
│  3. Returns complete config (database, JWT, Eureka, etc.)                   │
│  4. identity-service uses this config                                        │
│                                                                              │
│  IMPORTANT NOTES:                                                            │
│  ────────────────                                                           │
│  • Each service specifies its OWN database schema via ?currentSchema=xxx    │
│  • Each service has its own port (8081, 8082, 8083, etc.)                   │
│  • All services share the same PostgreSQL database but different schemas    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**NOTE:** In a shared config approach, you might have a `config/application.yml` 
that contains settings shared by all services. Our implementation uses 
per-service configs for explicitness and easier debugging.

---

### Step 4.7: Create Service-Specific Configurations

Each service gets its own complete configuration file. The Config Server serves these when services start up.

**File: `config-server/src/main/resources/configurations/identity-service.yml`**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# IDENTITY SERVICE CONFIGURATION
# This file is served by Config Server when identity-service starts up
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8081

spring:
  # ─────────────────────────────────────────────────────────────────────────
  # DATABASE SETTINGS
  # ─────────────────────────────────────────────────────────────────────────
  datasource:
    # ┌─────────────────────────────────────────────────────────────────────┐
    # │ JDBC URL FORMAT                                                     │
    # │                                                                     │
    # │ jdbc:postgresql://host:port/database?currentSchema=schema           │
    # │                                                                     │
    # │ currentSchema=identity ensures all tables for this service          │
    # │ are created in the 'identity' schema, not the public schema.        │
    # └─────────────────────────────────────────────────────────────────────┘
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=identity
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      # ┌─────────────────────────────────────────────────────────────────┐
      # │ DDL-AUTO: VALIDATE                                              │
      # │                                                                  │
      # │ We use Flyway for schema management, so Hibernate only          │
      # │ validates that entities match the database schema.              │
      # │ This catches mapping errors early.                              │
      # └─────────────────────────────────────────────────────────────────┘
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: identity
  flyway:
    enabled: true
    schemas: identity
    locations: classpath:db/migration

# ─────────────────────────────────────────────────────────────────────────────
# JWT CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────
jwt:
  # ┌─────────────────────────────────────────────────────────────────────────┐
  # │ JWT SECRET                                                              │
  # │                                                                         │
  # │ Used for signing tokens with HMAC-SHA256 algorithm.                    │
  # │ IMPORTANT: In production, use a strong secret stored in a vault!       │
  # │ The key must be at least 256 bits (32+ characters) for HS256.          │
  # └─────────────────────────────────────────────────────────────────────────┘
  secret: payflow-jwt-secret-key-change-in-production-minimum-256-bits-long
  # Access token: 15 minutes (900,000 milliseconds)
  access-token-expiry: 900000
  # Refresh token: 7 days (604,800,000 milliseconds)
  refresh-token-expiry: 604800000

# ─────────────────────────────────────────────────────────────────────────────
# EUREKA CLIENT
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# ─────────────────────────────────────────────────────────────────────────────
# SWAGGER / OPENAPI
# ─────────────────────────────────────────────────────────────────────────────
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```



**File: `config-server/src/main/resources/configurations/merchant-service.yml`**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# MERCHANT SERVICE CONFIGURATION
# This file is served by Config Server when merchant-service starts up
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=merchant
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: merchant
  flyway:
    enabled: true
    schemas: merchant
    locations: classpath:db/migration

# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# Swagger
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```



**File: `config-server/src/main/resources/configurations/payment-service.yml`**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# PAYMENT SERVICE CONFIGURATION
# This file is served by Config Server when payment-service starts up
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8083

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=payment
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: payment
  flyway:
    enabled: true
    schemas: payment
    locations: classpath:db/migration
  data:
    redis:
      host: localhost
      port: 6379

# ─────────────────────────────────────────────────────────────────────────────
# PAYMENT CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────
payment:
  # ┌─────────────────────────────────────────────────────────────────────────┐
  # │ PAYMENT BUSINESS RULES                                                  │
  # │                                                                         │
  # │ order-expiry: How long a customer has to complete payment               │
  # │ auth-expiry: How long an authorization hold is valid                   │
  # │ idempotency-key-ttl: How long we track duplicate requests               │
  # └─────────────────────────────────────────────────────────────────────────┘
  order-expiry-minutes: 30           # Orders expire after 30 minutes
  auth-expiry-days: 7                # Authorizations expire after 7 days
  idempotency-key-ttl-hours: 24     # Idempotency keys stored for 24 hours

# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# Swagger
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

# ─────────────────────────────────────────────────────────────────────────────
# RESILIENCE4J — Circuit breaker for routing-service calls
# ─────────────────────────────────────────────────────────────────────────────
resilience4j:
  circuitbreaker:
    instances:
      routing-service:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      routing-service:
        max-attempts: 2
        wait-duration: 200ms
```



---

## 5. Verification

### 5.1 Build the Module

```powershell
# From project root
cd config-server

# Clean and build
mvn clean package -DskipTests

# Expected: BUILD SUCCESS
```

### 5.2 Run the Application

```powershell
# Make sure Service Registry is still running on port 8761!

# Start Config Server
mvn spring-boot:run
```

**Expected console output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

INFO  --- ConfigServerApplication : Starting ConfigServerApplication
INFO  --- TomcatWebServer : Tomcat started on port 8888
INFO  --- DiscoveryClient : Starting registration with Eureka
INFO  --- ConfigServerApplication : Started in X.XXX seconds
```

### 5.3 Verify Eureka Registration

**Check Eureka Dashboard:** http://localhost:8761

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        EUREKA DASHBOARD                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Instances currently registered with Eureka                                  │
│  ──────────────────────────────────────────                                 │
│                                                                              │
│  Application      AMIs    Availability Zones    Status                      │
│  ───────────────────────────────────────────────────────                    │
│  CONFIG-SERVER    n/a     (1)                   UP (1) - config-server:8888 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**🎉 CONFIG-SERVER is now registered!**

### 5.4 Test Config Endpoints

```powershell
# Test 1: Get shared configuration
curl http://localhost:8888/application/default

# Expected: JSON with spring.datasource, eureka, logging settings
```

```powershell
# Test 2: Get identity-service configuration
curl http://localhost:8888/identity-service/default

# Expected: Merged config (application.yml + identity-service.yml)
# Should include jwt.access-token-expiry, server.port: 8081
```

```powershell
# Test 3: Get merchant-service configuration  
curl http://localhost:8888/merchant-service/default

# Expected: Merged config with merchant.id-prefix, server.port: 8082
```

**Sample response for identity-service:**
```json
{
  "name": "identity-service",
  "profiles": ["default"],
  "propertySources": [
    {
      "name": "classpath:/configurations/identity-service.yml",
      "source": {
        "server.port": 8081,
        "jwt.access-token-expiry": 900000,
        "jwt.refresh-token-expiry": 604800000,
        "spring.datasource.url": "jdbc:postgresql://localhost:5432/payflow?currentSchema=identity"
      }
    }
  ]
}
```

### 5.5 Test Health Endpoint

```powershell
curl http://localhost:8888/actuator/health

# Expected:
{
  "status": "UP",
  "components": {
    "discoveryComposite": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

---

## 6. File Structure After This Part

```
payflow-payment-gateway/
├── pom.xml                          (updated with config-server module)
├── common-lib/
├── service-registry/
└── config-server/                   ← NEW
    ├── pom.xml
    └── src/
        └── main/
            ├── java/
            │   └── com/
            │       └── payflow/
            │           └── config/
            │               └── ConfigServerApplication.java
            └── resources/
                ├── application.yml          ← Config Server's own config
                └── configurations/          ← Configs for OTHER services
                    ├── identity-service.yml     (identity-service config)
                    ├── merchant-service.yml     (merchant-service config)
                    └── payment-service.yml      (payment-service config)
```

---

## 7. Key Takeaways

| Concept | What You Learned |
|---------|------------------|
| **Centralized Config** | One place for all service configurations |
| **@EnableConfigServer** | Single annotation to create Config Server |
| **Native Profile** | Use local files instead of Git for dev |
| **Config Merging** | application.yml + {service}.yml merged |
| **Profiles** | Different configs for dev/prod environments |
| **Property Precedence** | Environment vars > Config Server > Local files |

---

## 8. Common Issues & Solutions

### Issue: Config Server can't register with Eureka
```
Connection refused: localhost:8761
```
**Solution:**
Make sure Service Registry is running first!
```powershell
# Check if Eureka is running
curl http://localhost:8761/actuator/health
```

### Issue: Config endpoint returns empty
```
curl http://localhost:8888/identity-service/default
# Returns: {"name":"identity-service","profiles":["default"],"propertySources":[]}
```
**Solution:**
Check that config files exist in the right location:
```powershell
dir config-server\src\main\resources\config
# Should show: application.yml, identity-service.yml, etc.
```

### Issue: YAML syntax error
```
Failed to load property source: classpath:/config/application.yml
```
**Solution:**
- YAML is indentation-sensitive (use 2 spaces, NOT tabs)
- Use online YAML validator: https://yamlvalidator.com/
- Check for trailing spaces

---

## 9. Understanding the Config Server System

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIG SERVER FILE ORGANIZATION                           │
│                                                                              │
│  Q: Why does config-server have its own application.yml AND a folder?       │
│                                                                              │
│  config-server/                                                              │
│  └── src/main/resources/                                                     │
│      ├── application.yml         ← Config Server's OWN config               │
│      │   (port 8888, Eureka settings, native profile)                       │
│      │                                                                       │
│      └── configurations/         ← Configs SERVED TO other services         │
│          ├── identity-service.yml                                           │
│          ├── merchant-service.yml                                           │
│          └── payment-service.yml                                            │
│                                                                              │
│  ANALOGY: A restaurant menu                                                  │
│                                                                              │
│  The restaurant (Config Server) has:                                         │
│  • Its own operating procedures (resources/application.yml)                  │
│  • Menus it serves to customers (resources/configurations/*.yml)            │
│                                                                              │
│  The restaurant's procedures ≠ The menu content                              │
│  Config Server's config ≠ Configs it serves                                  │
│                                                                              │
│  HOW SERVICES GET THEIR CONFIG:                                              │
│  ──────────────────────────────                                             │
│  1. identity-service starts up                                               │
│  2. Calls: GET http://localhost:8888/identity-service/default               │
│  3. Config Server reads configurations/identity-service.yml                 │
│  4. Returns the config as JSON                                               │
│  5. identity-service merges it with its local application.yml               │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Next Steps

**Keep both services running:**
- Terminal 1: Service Registry (port 8761)
- Terminal 2: Config Server (port 8888)

Open a new terminal for **Part 03: API Gateway**.

The API Gateway will:
1. Register with Eureka
2. Fetch configuration from Config Server
3. Route requests to backend services

---

**Next:** [Part 03: API Gateway](./part-03-api-gateway.md)

