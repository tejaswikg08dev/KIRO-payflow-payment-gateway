# Sprint 1, Part 01: Service Registry & Config Server

**Duration:** 3-4 hours  
**Prerequisites:** Sprint 0 completed, Docker infrastructure running

---

## 1. What We're Building

In this part, you'll build two foundational Spring Cloud services:

| Service | Port | Purpose |
|---------|------|---------|
| service-registry | 8761 | Service discovery (Eureka Server) |
| config-server | 8888 | Centralized configuration |

These are the backbone of microservices architecture - without them, services can't find each other or share configuration.

---

## 2. Concepts Deep Dive

### 2.1 Why Service Discovery?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    THE PROBLEM: HARDCODED URLS                               │
│                                                                              │
│  Without Service Discovery:                                                  │
│                                                                              │
│  api-gateway/application.yml:                                                │
│  ┌─────────────────────────────────────┐                                    │
│  │ routes:                             │                                    │
│  │   - id: identity                    │                                    │
│  │     uri: http://localhost:8081  ← HARDCODED!                             │
│  │   - id: merchant                    │                                    │
│  │     uri: http://localhost:8082  ← HARDCODED!                             │
│  │   - id: payment                     │                                    │
│  │     uri: http://localhost:8083  ← HARDCODED!                             │
│  └─────────────────────────────────────┘                                    │
│                                                                              │
│  Problems:                                                                   │
│  ❌ What if identity-service moves to port 9081?                            │
│  ❌ What if we run 3 instances of payment-service?                          │
│  ❌ What if a service crashes and restarts on different port?               │
│  ❌ Config change = restart ALL services                                    │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                    THE SOLUTION: SERVICE DISCOVERY                           │
│                                                                              │
│  With Eureka:                                                                │
│                                                                              │
│  api-gateway/application.yml:                                                │
│  ┌─────────────────────────────────────┐                                    │
│  │ routes:                             │                                    │
│  │   - id: identity                    │                                    │
│  │     uri: lb://identity-service  ← LOGICAL NAME                           │
│  │   - id: merchant                    │                                    │
│  │     uri: lb://merchant-service  ← LOGICAL NAME                           │
│  └─────────────────────────────────────┘                                    │
│                                                                              │
│  "lb://" = "load balanced" - Eureka resolves the actual address             │
│                                                                              │
│  Benefits:                                                                   │
│  ✅ Services register themselves with Eureka                                │
│  ✅ Gateway asks Eureka "where is identity-service?"                        │
│  ✅ Eureka returns current address(es)                                      │
│  ✅ Automatic load balancing across multiple instances                      │
│  ✅ Automatic failover if instance dies                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Eureka Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         EUREKA WORKFLOW                                      │
│                                                                              │
│                      ┌─────────────────────────┐                            │
│                      │    EUREKA SERVER        │                            │
│                      │    (Service Registry)   │                            │
│                      │                         │                            │
│                      │  Registry:              │                            │
│                      │  ┌───────────────────┐  │                            │
│                      │  │ identity-service  │  │                            │
│                      │  │ → 192.168.1.5:8081│  │                            │
│                      │  ├───────────────────┤  │                            │
│                      │  │ merchant-service  │  │                            │
│                      │  │ → 192.168.1.6:8082│  │                            │
│                      │  ├───────────────────┤  │                            │
│                      │  │ payment-service   │  │                            │
│                      │  │ → 192.168.1.7:8083│  │                            │
│                      │  │ → 192.168.1.8:8083│ ← 2 instances!               │
│                      │  └───────────────────┘  │                            │
│                      └───────────┬─────────────┘                            │
│                                  │                                           │
│         ┌────────────────────────┼────────────────────────┐                 │
│         │                        │                        │                 │
│         │ 1. Register            │ 2. Heartbeat           │ 3. Query        │
│         │ (startup)              │ (every 30s)            │ (on demand)     │
│         │                        │                        │                 │
│         ▼                        ▼                        ▼                 │
│  ┌─────────────┐          ┌─────────────┐          ┌─────────────┐         │
│  │  identity   │          │  merchant   │          │ api-gateway │         │
│  │  service    │          │  service    │          │             │         │
│  │             │          │             │          │ "Where is   │         │
│  │ "I'm here   │          │ "Still      │          │  identity?" │         │
│  │  at :8081"  │          │  alive!"    │          │             │         │
│  └─────────────┘          └─────────────┘          └─────────────┘         │
│                                                                              │
│  Key Concepts:                                                               │
│  • Register: Service tells Eureka its address on startup                    │
│  • Heartbeat: Service sends "I'm alive" every 30 seconds                    │
│  • Eviction: If no heartbeat for 90s, Eureka removes the service            │
│  • Query: Other services ask Eureka for addresses                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Why Config Server?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    THE PROBLEM: SCATTERED CONFIGURATION                      │
│                                                                              │
│  Without Config Server:                                                      │
│                                                                              │
│  identity-service/                 merchant-service/                        │
│  └── application.yml               └── application.yml                      │
│      db.url: localhost:5432            db.url: localhost:5432  ← DUPLICATE  │
│      db.user: payflow                  db.user: payflow        ← DUPLICATE  │
│      jwt.expiry: 86400                 eureka.url: ...         ← DUPLICATE  │
│                                                                              │
│  payment-service/                  webhook-service/                         │
│  └── application.yml               └── application.yml                      │
│      db.url: localhost:5432            db.url: localhost:5432  ← DUPLICATE  │
│      db.user: payflow                  db.user: payflow        ← DUPLICATE  │
│                                                                              │
│  Problems:                                                                   │
│  ❌ Same config repeated in 10+ services                                    │
│  ❌ Change DB password = edit 10+ files                                     │
│  ❌ Easy to miss one service                                                │
│  ❌ Secrets scattered everywhere                                            │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                    THE SOLUTION: CENTRALIZED CONFIG                          │
│                                                                              │
│  With Config Server:                                                         │
│                                                                              │
│  config-server/config/                                                       │
│  ├── application.yml          ← SHARED by all services                      │
│  │   db.url: localhost:5432                                                 │
│  │   db.user: payflow                                                       │
│  │   eureka.url: ...                                                        │
│  │                                                                           │
│  ├── identity-service.yml     ← SPECIFIC to identity                        │
│  │   jwt.expiry: 86400                                                      │
│  │                                                                           │
│  └── merchant-service.yml     ← SPECIFIC to merchant                        │
│      fee.percentage: 2.5                                                    │
│                                                                              │
│  Benefits:                                                                   │
│  ✅ Single source of truth                                                  │
│  ✅ Change once, all services get it                                        │
│  ✅ Environment-specific configs (dev, prod)                                │
│  ✅ Secrets in one secure place                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```


---

## 3. Prerequisites

Before starting, verify:

```powershell
# Check Java
java -version
# Should show: openjdk version "17.x.x"

# Check Maven
mvn -version
# Should show: Apache Maven 3.9.x

# Check Docker running
docker ps
# Should show postgres, redis, localstack from Sprint 0

# Check project structure
ls
# Should show: pom.xml, common-lib/, docker-compose.infra.yml
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

First, add the service-registry and config-server modules to the parent pom.xml.

**Open `pom.xml` (root) and add modules:**

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>     <!-- ADD THIS -->
    <module>config-server</module>        <!-- ADD THIS -->
</modules>
```

---

### Step 4.2: Create Service Registry Module

**Create folder structure:**

```powershell
# Create service-registry folder
mkdir service-registry
mkdir service-registry\src\main\java\com\payflow\registry
mkdir service-registry\src\main\resources
```

---

### Step 4.3: Create service-registry/pom.xml

Create `service-registry/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ═══════════════════════════════════════════════════════════════════
         Parent Reference
         Inherits Spring Boot version, common dependencies from parent
    ═══════════════════════════════════════════════════════════════════ -->
    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <!-- ═══════════════════════════════════════════════════════════════════
         Module Info
         This module's identity
    ═══════════════════════════════════════════════════════════════════ -->
    <artifactId>service-registry</artifactId>
    <name>PayFlow Service Registry</name>
    <description>Eureka Server for service discovery</description>

    <!-- ═══════════════════════════════════════════════════════════════════
         Dependencies
    ═══════════════════════════════════════════════════════════════════ -->
    <dependencies>
        <!-- 
        Eureka Server
        What it does:
        - Provides the Eureka Server implementation
        - Includes REST endpoints for registration
        - Includes web dashboard at /
        
        Why we need it:
        - This IS the service registry
        - Other services will register here
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>

        <!-- 
        Spring Boot Actuator
        What it does:
        - Provides health check endpoints
        - Provides metrics endpoints
        
        Why we need it:
        - Docker/Kubernetes can check if service is healthy
        - Monitoring tools can collect metrics
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <!-- ═══════════════════════════════════════════════════════════════════
         Build Configuration
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

**Key Points:**
- `spring-cloud-starter-netflix-eureka-server` - This single dependency gives us the complete Eureka Server
- Version is managed by parent POM's Spring Cloud BOM (Bill of Materials)

---

### Step 4.4: Create ServiceRegistryApplication.java

Create `service-registry/src/main/java/com/payflow/registry/ServiceRegistryApplication.java`:

```java
package com.payflow.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server Application
 * 
 * This is the service discovery server. All microservices register here,
 * and other services query here to find service locations.
 * 
 * @EnableEurekaServer - This single annotation:
 * 1. Starts an embedded Eureka Server
 * 2. Provides REST API for registration (/eureka/apps)
 * 3. Provides web dashboard (http://localhost:8761)
 * 4. Handles heartbeats and eviction
 */
@SpringBootApplication
@EnableEurekaServer  // ← This makes it a Eureka Server
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
```

**What happens when this starts:**
1. Spring Boot initializes
2. `@EnableEurekaServer` starts the Eureka Server
3. Web dashboard becomes available at port 8761
4. REST API ready for service registrations

---

### Step 4.5: Create service-registry/application.yml

Create `service-registry/src/main/resources/application.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# Service Registry (Eureka Server) Configuration
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# Server Configuration
# ─────────────────────────────────────────────────────────────────────────────
server:
  port: 8761  # Standard Eureka port (convention)

# ─────────────────────────────────────────────────────────────────────────────
# Application Name
# This name will appear in Eureka dashboard
# ─────────────────────────────────────────────────────────────────────────────
spring:
  application:
    name: service-registry

# ─────────────────────────────────────────────────────────────────────────────
# Eureka Server Configuration
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  instance:
    hostname: localhost
    
  client:
    # ─────────────────────────────────────────────────────────────────────────
    # Don't register with itself
    # Why? This IS the registry. It doesn't need to register as a client.
    # In production with multiple Eureka servers, you'd set this to true.
    # ─────────────────────────────────────────────────────────────────────────
    register-with-eureka: false
    
    # ─────────────────────────────────────────────────────────────────────────
    # Don't fetch registry from itself
    # Why? It's the source of truth. No need to fetch what it already has.
    # ─────────────────────────────────────────────────────────────────────────
    fetch-registry: false
    
    # ─────────────────────────────────────────────────────────────────────────
    # Eureka Server URL (points to itself)
    # ─────────────────────────────────────────────────────────────────────────
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

  server:
    # ─────────────────────────────────────────────────────────────────────────
    # Disable self-preservation in development
    # 
    # What is self-preservation?
    # If Eureka stops receiving heartbeats from many services at once,
    # it assumes there's a network issue (not that services died).
    # It keeps the registrations instead of removing them.
    # 
    # In development: Disable it (we restart services often)
    # In production: Keep it enabled (protects against network glitches)
    # ─────────────────────────────────────────────────────────────────────────
    enable-self-preservation: false
    
    # ─────────────────────────────────────────────────────────────────────────
    # How often to check for expired instances (milliseconds)
    # Default is 60000 (60 seconds). We use 5000 for faster dev feedback.
    # ─────────────────────────────────────────────────────────────────────────
    eviction-interval-timer-in-ms: 5000

# ─────────────────────────────────────────────────────────────────────────────
# Actuator (Health checks)
# ─────────────────────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

---

### Step 4.6: Verify Service Registry

**Build and run:**

```powershell
# Navigate to service-registry
cd service-registry

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.x)

... Started ServiceRegistryApplication in X.XXX seconds
```

**Open browser:** http://localhost:8761

You should see the Eureka dashboard with:
- System Status
- Currently no instances registered (that's expected!)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      EUREKA DASHBOARD                                        │
│                                                                              │
│  System Status                                                               │
│  ─────────────                                                              │
│  Environment: test                                                           │
│  Data center: default                                                        │
│  Current time: 2026-08-04 10:30:00                                          │
│  Uptime: 00:01:23                                                            │
│                                                                              │
│  Instances currently registered with Eureka                                  │
│  ──────────────────────────────────────────                                 │
│  Application        AMIs        Availability Zones    Status                │
│  ─────────────────────────────────────────────────────────────              │
│  No instances available                                                      │
│                                                                              │
│  General Info                                                                │
│  ────────────                                                               │
│  total-avail-memory: 512mb                                                  │
│  num-of-cpus: 4                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Keep this running** and open a new terminal for the next steps.


---

### Step 4.7: Create Config Server Module

**Create folder structure:**

```powershell
# Create config-server folder
mkdir config-server
mkdir config-server\src\main\java\com\payflow\config
mkdir config-server\src\main\resources
mkdir config-server\src\main\resources\config
```

---

### Step 4.8: Create config-server/pom.xml

Create `config-server/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- ═══════════════════════════════════════════════════════════════════
         Parent Reference
    ═══════════════════════════════════════════════════════════════════ -->
    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <!-- ═══════════════════════════════════════════════════════════════════
         Module Info
    ═══════════════════════════════════════════════════════════════════ -->
    <artifactId>config-server</artifactId>
    <name>PayFlow Config Server</name>
    <description>Centralized configuration server</description>

    <!-- ═══════════════════════════════════════════════════════════════════
         Dependencies
    ═══════════════════════════════════════════════════════════════════ -->
    <dependencies>
        <!-- 
        Spring Cloud Config Server
        What it does:
        - Serves configuration to all services
        - Supports file-based, Git-based, or vault-based config
        - Provides REST API: /application/profile
        
        Why we need it:
        - Central place for all configuration
        - Services fetch config on startup
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>

        <!-- 
        Eureka Client
        What it does:
        - Registers this service with Eureka
        
        Why we need it:
        - Other services find Config Server via Eureka
        - No hardcoded URL needed
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Actuator for health checks -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <!-- ═══════════════════════════════════════════════════════════════════
         Build Configuration
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

### Step 4.9: Create ConfigServerApplication.java

Create `config-server/src/main/java/com/payflow/config/ConfigServerApplication.java`:

```java
package com.payflow.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server
 * 
 * This server provides centralized configuration for all microservices.
 * Services fetch their configuration from here on startup.
 * 
 * @EnableConfigServer - This annotation:
 * 1. Starts the Config Server
 * 2. Provides REST endpoints for config /{application}/{profile}
 * 3. Watches config source (files or Git) for changes
 * 
 * How it works:
 * 1. Config files are stored in config/ folder (or Git repo)
 * 2. Service requests config: GET /identity-service/default
 * 3. Config Server returns merged config (application.yml + identity-service.yml)
 */
@SpringBootApplication
@EnableConfigServer  // ← This makes it a Config Server
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

---

### Step 4.10: Create config-server/application.yml

Create `config-server/src/main/resources/application.yml`:

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# Config Server Configuration
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# Server Configuration
# ─────────────────────────────────────────────────────────────────────────────
server:
  port: 8888  # Standard Config Server port (convention)

# ─────────────────────────────────────────────────────────────────────────────
# Application Name
# ─────────────────────────────────────────────────────────────────────────────
spring:
  application:
    name: config-server
    
  # ─────────────────────────────────────────────────────────────────────────
  # Config Source: Local filesystem
  # 
  # In production, you might use:
  # - Git repository (most common)
  # - HashiCorp Vault (for secrets)
  # - AWS S3
  # 
  # For development, we use native (local files) for simplicity.
  # ─────────────────────────────────────────────────────────────────────────
  profiles:
    active: native  # Use local filesystem
    
  cloud:
    config:
      server:
        native:
          # ─────────────────────────────────────────────────────────────────
          # Where config files are stored
          # classpath:/config means: src/main/resources/config/
          # ─────────────────────────────────────────────────────────────────
          search-locations: classpath:/config

# ─────────────────────────────────────────────────────────────────────────────
# Eureka Client Configuration
# Register with Eureka so other services can find us
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

# ─────────────────────────────────────────────────────────────────────────────
# Actuator Configuration
# ─────────────────────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

---

### Step 4.11: Create Shared Configuration

Now create the configuration files that all services will use.

**Create `config-server/src/main/resources/config/application.yml`:**

This is the SHARED configuration - all services will get these values.

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# SHARED CONFIGURATION
# Every service will receive these settings
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# Eureka Client (all services register with Eureka)
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    # ─────────────────────────────────────────────────────────────────────────
    # Lease renewal interval
    # How often the service sends heartbeat to Eureka
    # Default: 30 seconds
    # ─────────────────────────────────────────────────────────────────────────
    lease-renewal-interval-in-seconds: 10
    # ─────────────────────────────────────────────────────────────────────────
    # Lease expiration duration
    # If no heartbeat for this long, Eureka removes the service
    # Default: 90 seconds
    # ─────────────────────────────────────────────────────────────────────────
    lease-expiration-duration-in-seconds: 30

# ─────────────────────────────────────────────────────────────────────────────
# Database Configuration (shared by services that use PostgreSQL)
# ─────────────────────────────────────────────────────────────────────────────
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow
    username: payflow
    password: payflow123
    driver-class-name: org.postgresql.Driver
    
  jpa:
    hibernate:
      # ─────────────────────────────────────────────────────────────────────
      # DDL auto mode
      # validate: Check schema matches entities (production)
      # update: Auto-create/update tables (development)
      # create: Drop and create on startup (testing)
      # none: Do nothing (when using Flyway/Liquibase)
      # ─────────────────────────────────────────────────────────────────────
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

# ─────────────────────────────────────────────────────────────────────────────
# Redis Configuration (shared by services that use Redis)
# ─────────────────────────────────────────────────────────────────────────────
  data:
    redis:
      host: localhost
      port: 6379

# ─────────────────────────────────────────────────────────────────────────────
# Actuator Configuration (health checks for all services)
# ─────────────────────────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

# ─────────────────────────────────────────────────────────────────────────────
# Logging Configuration
# ─────────────────────────────────────────────────────────────────────────────
logging:
  level:
    root: INFO
    com.payflow: DEBUG
    org.springframework.security: DEBUG
```


---

### Step 4.12: Create Service-Specific Configs

**Create `config-server/src/main/resources/config/identity-service.yml`:**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# IDENTITY SERVICE CONFIGURATION
# Settings specific to the authentication service
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8081

spring:
  jpa:
    properties:
      hibernate:
        # Use identity schema for this service
        default_schema: identity

# ─────────────────────────────────────────────────────────────────────────────
# JWT Configuration
# ─────────────────────────────────────────────────────────────────────────────
jwt:
  # Token expiration time in seconds (24 hours = 86400)
  expiration: 86400
  # Issuer claim in the token
  issuer: payflow
```

**Create `config-server/src/main/resources/config/merchant-service.yml`:**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# MERCHANT SERVICE CONFIGURATION
# Settings specific to the merchant onboarding service
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8082

spring:
  jpa:
    properties:
      hibernate:
        # Use merchant schema for this service
        default_schema: merchant
```

**Create `config-server/src/main/resources/config/api-gateway.yml`:**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# API GATEWAY CONFIGURATION
# Routes and filters for the gateway
# ═══════════════════════════════════════════════════════════════════════════

server:
  port: 8080

spring:
  cloud:
    gateway:
      # ─────────────────────────────────────────────────────────────────────
      # Enable service discovery routing
      # This allows: lb://service-name (load balanced)
      # ─────────────────────────────────────────────────────────────────────
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

---

### Step 4.13: Verify Config Server

**Build and run Config Server:**

```powershell
# In a new terminal (keep Service Registry running!)
cd config-server

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

**Expected output:**
```
Started ConfigServerApplication in X.XXX seconds
```

**Test the Config Server API:**

```powershell
# Get shared config
curl http://localhost:8888/application/default

# Get identity-service config
curl http://localhost:8888/identity-service/default

# Get merchant-service config
curl http://localhost:8888/merchant-service/default
```

**Expected response for `/identity-service/default`:**
```json
{
  "name": "identity-service",
  "profiles": ["default"],
  "propertySources": [
    {
      "name": "classpath:/config/identity-service.yml",
      "source": {
        "server.port": 8081,
        "jwt.expiration": 86400,
        ...
      }
    },
    {
      "name": "classpath:/config/application.yml",
      "source": {
        "eureka.client.service-url.defaultZone": "http://localhost:8761/eureka/",
        "spring.datasource.url": "jdbc:postgresql://localhost:5432/payflow",
        ...
      }
    }
  ]
}
```

**Check Eureka dashboard:** http://localhost:8761

You should now see CONFIG-SERVER registered!

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Instances currently registered with Eureka                                  │
│  ──────────────────────────────────────────                                 │
│  Application        AMIs        Availability Zones    Status                │
│  ─────────────────────────────────────────────────────────────              │
│  CONFIG-SERVER      n/a         n/a                   UP (1)               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Verification

### 5.1 Verification Checklist

| Check | Command/Action | Expected Result |
|-------|----------------|-----------------|
| Service Registry running | http://localhost:8761 | Eureka dashboard visible |
| Config Server running | http://localhost:8888/actuator/health | `{"status":"UP"}` |
| Config Server registered | http://localhost:8761 | CONFIG-SERVER in list |
| Shared config available | `curl http://localhost:8888/application/default` | Returns YAML config |
| Identity config available | `curl http://localhost:8888/identity-service/default` | Returns merged config |

### 5.2 Test Script

Create a test script to verify everything:

```powershell
# test-sprint1-part01.ps1

Write-Host "Testing Sprint 1 Part 01..." -ForegroundColor Green

# Test 1: Eureka Health
$eurekaHealth = Invoke-RestMethod -Uri "http://localhost:8761/actuator/health" -ErrorAction SilentlyContinue
if ($eurekaHealth.status -eq "UP") {
    Write-Host "✓ Eureka Server is UP" -ForegroundColor Green
} else {
    Write-Host "✗ Eureka Server is DOWN" -ForegroundColor Red
}

# Test 2: Config Server Health
$configHealth = Invoke-RestMethod -Uri "http://localhost:8888/actuator/health" -ErrorAction SilentlyContinue
if ($configHealth.status -eq "UP") {
    Write-Host "✓ Config Server is UP" -ForegroundColor Green
} else {
    Write-Host "✗ Config Server is DOWN" -ForegroundColor Red
}

# Test 3: Config Server returns config
$config = Invoke-RestMethod -Uri "http://localhost:8888/application/default" -ErrorAction SilentlyContinue
if ($config.name -eq "application") {
    Write-Host "✓ Config Server returns configuration" -ForegroundColor Green
} else {
    Write-Host "✗ Config Server not returning config" -ForegroundColor Red
}

Write-Host "`nAll tests completed!" -ForegroundColor Cyan
```

---

## 6. File Structure After This Part

```
payflow-payment-gateway/
├── pom.xml                           (updated with new modules)
├── common-lib/                       (from Sprint 0)
│
├── service-registry/                 ← NEW!
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/payflow/registry/
│       │   └── ServiceRegistryApplication.java
│       └── resources/
│           └── application.yml
│
└── config-server/                    ← NEW!
    ├── pom.xml
    └── src/main/
        ├── java/com/payflow/config/
        │   └── ConfigServerApplication.java
        └── resources/
            ├── application.yml
            └── config/               ← Configuration files
                ├── application.yml        (shared)
                ├── identity-service.yml   (identity specific)
                ├── merchant-service.yml   (merchant specific)
                └── api-gateway.yml        (gateway specific)
```

---

## 7. Key Takeaways

### Service Registry (Eureka)

| Concept | What It Does | Why It Matters |
|---------|--------------|----------------|
| Service Discovery | Services register and find each other | No hardcoded URLs |
| Heartbeat | Services send "I'm alive" signal | Auto-remove dead services |
| Load Balancing | `lb://service-name` distributes requests | Scale services easily |
| Self-Preservation | Protects against network glitches | Prevents mass deregistration |

### Config Server

| Concept | What It Does | Why It Matters |
|---------|--------------|----------------|
| Centralized Config | All config in one place | Single source of truth |
| Profile Support | Different config for dev/prod | Environment-specific settings |
| Config Merging | Shared + service-specific | DRY principle |
| Dynamic Updates | Refresh config without restart | (with Spring Cloud Bus) |

---

## 8. Common Issues & Solutions

### Issue 1: Eureka not starting

```
Error: Port 8761 already in use
```

**Solution:**
```powershell
# Find process using port 8761
netstat -ano | findstr "8761"
# Kill the process
taskkill /PID <process-id> /F
```

### Issue 2: Config Server can't find config files

```
Error: Could not locate PropertySource
```

**Solution:**
- Check folder path: `src/main/resources/config/`
- Check file names match service names exactly
- Verify `spring.profiles.active: native` is set

### Issue 3: Config Server not registering with Eureka

**Solution:**
- Start Service Registry FIRST
- Wait 30 seconds for registration
- Check `eureka.client.service-url.defaultZone` is correct

---

## 9. Next Steps

**Congratulations!** You've built the foundation of microservices:
- ✅ Service Registry for discovery
- ✅ Config Server for centralized configuration

**Continue to:** [Part 02: API Gateway](./part-02-api-gateway.md)

In Part 02, you'll build:
- API Gateway with routing
- JWT authentication filter
- Rate limiting

---

**End of Sprint 1, Part 01**

*Next: API Gateway with JWT Authentication*
