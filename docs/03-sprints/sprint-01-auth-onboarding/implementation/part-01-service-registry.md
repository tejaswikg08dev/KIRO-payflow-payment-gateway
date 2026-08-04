# Sprint 1, Part 01: Service Registry (Eureka Server)

**Duration:** 1.5-2 hours  
**Prerequisites:** Sprint 0 completed, Docker infrastructure running

---

## 1. What We're Building

In this part, you'll build the **Service Registry** - the backbone of microservices communication.

| Component | Port | Purpose |
|-----------|------|---------|
| service-registry | 8761 | Service discovery using Netflix Eureka |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     SERVICE REGISTRY ROLE                                    │
│                                                                              │
│  Without Service Registry:              With Service Registry:               │
│  ──────────────────────────              ─────────────────────               │
│                                                                              │
│  api-gateway                            ┌─────────────────────┐             │
│  └── routes:                            │  SERVICE REGISTRY   │             │
│      identity: localhost:8081 ← HARD    │                     │             │
│      merchant: localhost:8082 ← CODED   │  "identity" → 8081  │             │
│      payment:  localhost:8083 ← URLS    │  "merchant" → 8082  │             │
│                                         │  "payment"  → 8083  │             │
│  ❌ Change port = edit all configs      └─────────┬───────────┘             │
│  ❌ Add instance = edit all configs               │                          │
│  ❌ Service dies = manual intervention            │ Gateway asks:            │
│                                                   │ "Where is identity?"     │
│                                                   ▼                          │
│                                          Gateway routes to                   │
│                                          CURRENT address(es)                 │
│                                                                              │
│                                         ✅ Dynamic discovery                 │
│                                         ✅ Auto load balancing               │
│                                         ✅ Auto failover                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Concepts Deep Dive

### 2.1 What is Service Discovery?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE DISCOVERY EXPLAINED                               │
│                                                                              │
│  ANALOGY: Phone Directory                                                    │
│  ─────────────────────────                                                  │
│                                                                              │
│  Imagine you want to call "Pizza Place" but don't know their number.        │
│                                                                              │
│  Without directory:     │     With directory:                                │
│  "Call 555-1234"        │     "Call Pizza Place"                            │
│       ↓                 │          ↓                                         │
│  What if they           │     Directory looks up                             │
│  change number?         │     current number                                 │
│       ↓                 │          ↓                                         │
│  Call fails!            │     Returns: 555-9999                              │
│                         │     (even if number changed)                       │
│                                                                              │
│  Service Discovery = Phone Directory for Microservices                      │
│                                                                              │
│  SERVICE                          EUREKA (Directory)                         │
│  ───────                          ──────────────────                        │
│  identity-service                 "identity-service"                         │
│  Actual address: 192.168.1.5:8081 → registered as logical name              │
│                                                                              │
│  CALLER (API Gateway)             LOOKUP                                     │
│  ────────────────────             ──────                                    │
│  "Route to identity-service"      Eureka returns: 192.168.1.5:8081          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Eureka Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         EUREKA COMPONENTS                                    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     EUREKA SERVER                                    │   │
│  │                     (Service Registry)                               │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │                    REGISTRY TABLE                             │  │   │
│  │  │                                                               │  │   │
│  │  │  Service Name      │  Instance ID        │  Address          │  │   │
│  │  │  ──────────────────┼─────────────────────┼─────────────────  │  │   │
│  │  │  identity-service  │  instance-1         │  192.168.1.5:8081 │  │   │
│  │  │  identity-service  │  instance-2         │  192.168.1.6:8081 │  │   │
│  │  │  merchant-service  │  instance-1         │  192.168.1.7:8082 │  │   │
│  │  │  api-gateway       │  instance-1         │  192.168.1.8:8080 │  │   │
│  │  │                                                               │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  │                                                                      │   │
│  │  REST API Endpoints:                                                 │   │
│  │  • POST /eureka/apps/{appName}    - Register instance               │   │
│  │  • DELETE /eureka/apps/{appName}  - Deregister instance             │   │
│  │  • PUT /eureka/apps/{appName}     - Send heartbeat                  │   │
│  │  • GET /eureka/apps               - Get all registrations           │   │
│  │                                                                      │   │
│  │  Web Dashboard: http://localhost:8761                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 Registration & Heartbeat Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE LIFECYCLE WITH EUREKA                             │
│                                                                              │
│  TIME    SERVICE                    EUREKA SERVER                            │
│  ────    ───────                    ─────────────                            │
│                                                                              │
│  T=0     identity-service starts                                             │
│          │                                                                   │
│          │ 1. REGISTER                                                       │
│          │ POST /eureka/apps/identity-service                                │
│          │ Body: {host: "192.168.1.5", port: 8081}                          │
│          │────────────────────────────────────────►│                        │
│          │                                         │ Adds to registry       │
│          │◄────────────────────────────────────────│ Returns: 204 OK        │
│          │                                                                   │
│  T=30s   │ 2. HEARTBEAT (every 30 seconds)                                  │
│          │ PUT /eureka/apps/identity-service/instance-1                     │
│          │────────────────────────────────────────►│                        │
│          │                                         │ Updates lastSeen       │
│          │◄────────────────────────────────────────│ Returns: 200 OK        │
│          │                                                                   │
│  T=60s   │ 3. HEARTBEAT                                                     │
│          │────────────────────────────────────────►│                        │
│          │                                                                   │
│  T=90s   │ (Service crashes - no heartbeat)                                 │
│          │                                                                   │
│  T=120s  │                                         │ 4. EVICTION            │
│          │                                         │ No heartbeat for 90s   │
│          │                                         │ Remove from registry   │
│                                                                              │
│  T=150s  │ 5. Service restarts                                              │
│          │ POST /eureka/apps/identity-service                                │
│          │────────────────────────────────────────►│ Re-registered!         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```



### 2.4 Why Netflix Eureka?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE DISCOVERY OPTIONS                                 │
│                                                                              │
│  Option              │ Type           │ Best For                            │
│  ────────────────────┼────────────────┼────────────────────────────────     │
│  Netflix Eureka      │ AP (Available) │ Microservices, Spring Cloud        │
│  HashiCorp Consul    │ CP (Consistent)│ Multi-datacenter, K/V store        │
│  Apache Zookeeper    │ CP (Consistent)│ Big data (Kafka, Hadoop)           │
│  Kubernetes DNS      │ Built-in       │ Kubernetes environments             │
│                                                                              │
│  We use Eureka because:                                                      │
│  ✅ Native Spring Cloud integration                                         │
│  ✅ Proven at Netflix scale (millions of requests/day)                     │
│  ✅ AP system - favors availability over consistency                        │
│  ✅ Self-preservation mode for network partitions                           │
│  ✅ Built-in web dashboard                                                  │
│  ✅ Simple REST API                                                         │
│                                                                              │
│  AP vs CP:                                                                   │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │ AP (Eureka): If network splits, both sides keep working            │    │
│  │              Old data is OK, availability is priority               │    │
│  │                                                                     │    │
│  │ CP (Consul): If network splits, one side stops accepting writes    │    │
│  │              Consistency is priority, may become unavailable        │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  For microservices: We prefer the system to keep running with               │
│  slightly stale data rather than stop completely.                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Prerequisites

Before starting, verify your environment:

```powershell
# 1. Check Java 17
java -version
# Expected: openjdk version "17.x.x"

# 2. Check Maven
mvn -version
# Expected: Apache Maven 3.9.x

# 3. Check Docker is running
docker ps
# Expected: postgres, redis, localstack containers from Sprint 0

# 4. Check project root
cd C:\path\to\payflow-payment-gateway
dir
# Expected: pom.xml, common-lib folder, docker-compose-infra.yml
```

---

## 4. Step-by-Step Implementation

### Step 4.1: Update Parent POM

Add the service-registry module to the parent `pom.xml`.

**File: `pom.xml` (project root)**

Find the `<modules>` section and add:

```xml
<modules>
    <module>common-lib</module>
    <module>service-registry</module>  <!-- ADD THIS LINE -->
</modules>
```

**Why?**
- Maven needs to know about all modules in the project
- This enables building all modules with `mvn clean install` from root

---

### Step 4.2: Create Folder Structure

```powershell
# Create the service-registry module structure
mkdir service-registry
mkdir service-registry\src
mkdir service-registry\src\main
mkdir service-registry\src\main\java
mkdir service-registry\src\main\java\com
mkdir service-registry\src\main\java\com\payflow
mkdir service-registry\src\main\java\com\payflow\registry
mkdir service-registry\src\main\resources

# Verify structure
tree service-registry /F
```

Expected structure:
```
service-registry/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── payflow/
│       │           └── registry/
│       └── resources/
└── pom.xml (we'll create this next)
```

---

### Step 4.3: Create pom.xml

**File: `service-registry/pom.xml`**

Type this file line by line:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
```

**Line-by-line explanation:**
- `<?xml ...>` - XML declaration, must be first line
- `<project ...>` - Root element with Maven namespace
- `<modelVersion>4.0.0</modelVersion>` - POM version (always 4.0.0)

```xml
    <!-- ═══════════════════════════════════════════════════════════════════
         PARENT REFERENCE
         Inherits from root pom.xml
    ═══════════════════════════════════════════════════════════════════ -->
    <parent>
        <groupId>com.payflow</groupId>
        <artifactId>payflow-payment-gateway</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
```

**Why parent?**
- Inherits Spring Boot version
- Inherits Spring Cloud version
- Inherits common dependencies
- Inherits plugin configurations

```xml
    <!-- ═══════════════════════════════════════════════════════════════════
         MODULE IDENTITY
    ═══════════════════════════════════════════════════════════════════ -->
    <artifactId>service-registry</artifactId>
    <name>PayFlow Service Registry</name>
    <description>Netflix Eureka Server for service discovery</description>
```

**What each does:**
- `artifactId` - Unique name within the group (used in filenames)
- `name` - Human-readable name (shown in IDE)
- `description` - Documentation

```xml
    <!-- ═══════════════════════════════════════════════════════════════════
         DEPENDENCIES
    ═══════════════════════════════════════════════════════════════════ -->
    <dependencies>
        
        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ EUREKA SERVER                                                    │
        │                                                                  │
        │ What it provides:                                                │
        │ • Eureka Server implementation                                   │
        │ • REST API for registration (/eureka/apps/*)                    │
        │ • Web dashboard (port 8761)                                      │
        │ • Heartbeat handling                                             │
        │ • Instance eviction                                              │
        │                                                                  │
        │ One dependency = complete service registry!                      │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
```

**Note:** No `<version>` tag! 
- Version is managed by Spring Cloud BOM in parent pom.xml
- This ensures all Spring Cloud components use compatible versions

```xml
        <!-- 
        ┌─────────────────────────────────────────────────────────────────┐
        │ SPRING BOOT ACTUATOR                                            │
        │                                                                  │
        │ What it provides:                                                │
        │ • /actuator/health - Health check endpoint                      │
        │ • /actuator/info - Application info endpoint                    │
        │ • /actuator/metrics - Metrics endpoint                          │
        │                                                                  │
        │ Why we need it:                                                  │
        │ • Docker health checks call /actuator/health                    │
        │ • Kubernetes probes use health endpoint                         │
        │ • Monitoring tools scrape metrics                               │
        └─────────────────────────────────────────────────────────────────┘
        -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

    </dependencies>
```

```xml
    <!-- ═══════════════════════════════════════════════════════════════════
         BUILD CONFIGURATION
    ═══════════════════════════════════════════════════════════════════ -->
    <build>
        <plugins>
            <!--
            Spring Boot Maven Plugin
            • Creates executable JAR (fat JAR with all dependencies)
            • Enables: mvn spring-boot:run
            • Adds manifest entries for java -jar execution
            -->
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

**File: `service-registry/src/main/java/com/payflow/registry/ServiceRegistryApplication.java`**

```java
package com.payflow.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * SERVICE REGISTRY APPLICATION
 * Netflix Eureka Server for Service Discovery
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * This class starts the Eureka Server that acts as a service registry.
 * All microservices will register here and discover each other through here.
 * 
 * Key Concepts:
 * ─────────────
 * @SpringBootApplication - Combines three annotations:
 *   • @Configuration - This class can define beans
 *   • @EnableAutoConfiguration - Auto-configure based on classpath
 *   • @ComponentScan - Scan for components in this package
 * 
 * @EnableEurekaServer - This single annotation:
 *   • Starts embedded Eureka Server
 *   • Exposes REST API at /eureka/*
 *   • Provides web dashboard at root URL
 *   • Manages service registry in memory
 * 
 * What happens on startup:
 * ────────────────────────
 * 1. Spring Boot initializes application context
 * 2. @EnableEurekaServer triggers Eureka Server auto-configuration
 * 3. Eureka Server starts listening on configured port (8761)
 * 4. Web dashboard becomes available
 * 5. REST API ready for service registrations
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    /**
     * Application entry point.
     * 
     * SpringApplication.run() does:
     * 1. Creates ApplicationContext
     * 2. Registers beans from classpath scanning
     * 3. Starts embedded server (Tomcat by default)
     * 4. Triggers ApplicationReadyEvent
     * 
     * @param args Command line arguments (can override properties)
     *             Example: --server.port=8762
     */
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
```



---

### Step 4.5: Create application.yml

**File: `service-registry/src/main/resources/application.yml`**

```yaml
# ═══════════════════════════════════════════════════════════════════════════
# SERVICE REGISTRY (EUREKA SERVER) CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════
# 
# This file configures the Eureka Server behavior.
# YAML format uses indentation (2 spaces) to show hierarchy.
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# SERVER SETTINGS
# ─────────────────────────────────────────────────────────────────────────────
server:
  # Port 8761 is the conventional Eureka port
  # Like how web servers use 80 and MySQL uses 3306
  port: 8761

# ─────────────────────────────────────────────────────────────────────────────
# SPRING APPLICATION SETTINGS
# ─────────────────────────────────────────────────────────────────────────────
spring:
  application:
    # This name appears in:
    # • Eureka dashboard
    # • Log files
    # • Actuator /info endpoint
    name: service-registry

# ─────────────────────────────────────────────────────────────────────────────
# EUREKA CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────
eureka:
  
  # INSTANCE SETTINGS
  # How this server identifies itself
  instance:
    hostname: localhost
    
  # CLIENT SETTINGS  
  # How this server behaves as a Eureka client
  # (Even servers can be clients in a cluster)
  client:
    
    # ┌─────────────────────────────────────────────────────────────────────┐
    # │ register-with-eureka: false                                         │
    # │                                                                     │
    # │ Should this server register ITSELF with Eureka?                     │
    # │                                                                     │
    # │ false = This IS the registry, doesn't need to register              │
    # │ true  = Use in production when you have multiple Eureka servers    │
    # │         They register with each other for high availability         │
    # └─────────────────────────────────────────────────────────────────────┘
    register-with-eureka: false
    
    # ┌─────────────────────────────────────────────────────────────────────┐
    # │ fetch-registry: false                                               │
    # │                                                                     │
    # │ Should this server fetch registry from another Eureka server?       │
    # │                                                                     │
    # │ false = It IS the source of truth, nothing to fetch                │
    # │ true  = Use in production for Eureka cluster replication           │
    # └─────────────────────────────────────────────────────────────────────┘
    fetch-registry: false
    
    # Where Eureka clients should register
    # ${eureka.instance.hostname} = localhost (from above)
    # ${server.port} = 8761 (from above)
    # Result: http://localhost:8761/eureka/
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

  # SERVER SETTINGS
  # Eureka Server specific configuration
  server:
    
    # ┌─────────────────────────────────────────────────────────────────────┐
    # │ WAIT TIME WHEN SYNC EMPTY                                           │
    # │                                                                     │
    # │ How long to wait on startup before accepting registry requests      │
    # │ when there are no instances to sync from (single instance mode)     │
    # │                                                                     │
    # │ 0 = Don't wait, start accepting immediately                        │
    # │     Good for development with single Eureka instance               │
    # └─────────────────────────────────────────────────────────────────────┘
    wait-time-in-ms-when-sync-empty: 0
    
    # ┌─────────────────────────────────────────────────────────────────────┐
    # │ EVICTION INTERVAL                                                   │
    # │                                                                     │
    # │ How often Eureka checks for dead instances (milliseconds)           │
    # │                                                                     │
    # │ Default: 60000 (60 seconds)                                         │
    # │ Our setting: 10000 (10 seconds) for faster feedback                │
    # │                                                                     │
    # │ Logic: If no heartbeat received for 3x intervals,                   │
    # │        instance is evicted from registry                            │
    # └─────────────────────────────────────────────────────────────────────┘
    eviction-interval-timer-in-ms: 10000
```

---

## 5. Verification

### 5.1 Build the Module

```powershell
# From project root
cd service-registry

# Clean and build
mvn clean package -DskipTests

# Expected output:
# [INFO] BUILD SUCCESS
# [INFO] Building jar: target/service-registry-1.0.0-SNAPSHOT.jar
```

### 5.2 Run the Application

```powershell
# Start the application
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
 :: Spring Boot ::                (v3.2.x)

INFO  --- ServiceRegistryApplication : Starting ServiceRegistryApplication
INFO  --- ServiceRegistryApplication : No active profile set
INFO  --- TomcatWebServer : Tomcat started on port 8761
INFO  --- ServiceRegistryApplication : Started in X.XXX seconds
```

### 5.3 Access the Dashboard

Open browser: **http://localhost:8761**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        EUREKA DASHBOARD                                      │
│                        http://localhost:8761                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  System Status                                                               │
│  ─────────────                                                              │
│  Environment .............. test                                             │
│  Data center .............. default                                          │
│  Current time ............. 2026-08-04 10:30:00 UTC                         │
│  Uptime ................... 0 days 00:00:45                                 │
│  Lease expiration enabled . true                                             │
│  Renews threshold ......... 1                                               │
│  Renews (last min) ........ 0                                               │
│                                                                              │
│  DS Replicas                                                                 │
│  ───────────                                                                │
│  (none configured - standalone mode)                                         │
│                                                                              │
│  Instances currently registered with Eureka                                  │
│  ──────────────────────────────────────────                                 │
│                                                                              │
│  Application    AMIs    Availability Zones    Status                        │
│  ───────────────────────────────────────────────────────                    │
│  No instances available                                                      │
│                                                                              │
│  General Info                                                                │
│  ────────────                                                               │
│  total-avail-memory ...... 512 MB                                           │
│  environment ............. test                                              │
│  num-of-cpus ............. 8                                                │
│  current-memory-usage .... 128 MB (25%)                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**What you see:**
- "No instances available" - Correct! No services have registered yet
- This will populate as we add more services

### 5.4 Test Health Endpoint

```powershell
# In a new terminal
curl http://localhost:8761/actuator/health

# Expected response:
{
  "status": "UP",
  "components": {
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
├── pom.xml                          (updated with service-registry module)
├── common-lib/
│   └── ...
└── service-registry/                ← NEW
    ├── pom.xml
    └── src/
        └── main/
            ├── java/
            │   └── com/
            │       └── payflow/
            │           └── registry/
            │               └── ServiceRegistryApplication.java
            └── resources/
                └── application.yml
```

---

## 7. Key Takeaways

| Concept | What You Learned |
|---------|------------------|
| **Service Discovery** | Services register with Eureka, others query to find them |
| **@EnableEurekaServer** | Single annotation to create a full Eureka Server |
| **Heartbeat** | Services send "I'm alive" every 30 seconds |
| **Eviction** | No heartbeat for 90s = removed from registry |
| **Self-Preservation** | Eureka protects against network issues in production |

---

## 8. Common Issues & Solutions

### Issue: Port 8761 already in use
```
Error: Web server failed to start. Port 8761 was already in use.
```
**Solution:**
```powershell
# Find what's using the port
netstat -ano | findstr :8761

# Kill the process (replace PID)
taskkill /PID <pid> /F

# Or change port in application.yml
server:
  port: 8762
```

### Issue: Maven dependency resolution failed
```
Error: Could not resolve dependencies
```
**Solution:**
```powershell
# Clear Maven cache and retry
mvn dependency:purge-local-repository
mvn clean install -U
```

### Issue: Dashboard shows warning
```
EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP
```
**Solution:**
This appears when self-preservation is triggered. This is normal behavior in development when services are frequently restarted. The warning will clear itself. If you want faster instance eviction, the current config already has a lower eviction interval (10 seconds instead of default 60 seconds).

---

## 9. Next Steps

**Keep the Service Registry running** in this terminal.

Open a new terminal for **Part 02: Config Server**.

The Config Server will be the first service to register with this Eureka Server!

---

**Next:** [Part 02: Config Server](./part-02-config-server.md)

