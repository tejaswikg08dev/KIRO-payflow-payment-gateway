# Hands-On Guide — Phase 3 Part 2: Service Registry (Eureka Server)

## Goal

By the end of Part 2, you will have:
- Eureka Server running on port 8761
- Dashboard accessible at http://localhost:8761
- Understanding of WHY we need service discovery
- Understanding of how services register and find each other
- Ready for other services to register
- Git commit

## Prerequisites

- Part 1 completed (parent POM + common-lib compile)
- Java 17 and Maven installed

---

## What Is Service Discovery? (Real-World Analogy)

```
IMAGINE A COMPANY WITH 50 EMPLOYEES:

WITHOUT a directory/phone book:
├── You need to talk to the "Finance" person
├── You ask around: "Where is Finance sitting today?"
├── Someone tells you: "Third floor, seat 42"
├── Next week they moved to "Second floor, seat 15"
├── You ask around AGAIN every time you need them
└── If they're on leave → you don't know → you walk there for nothing

WITH a company directory (= Eureka):
├── Everyone registers: "I'm Finance, I sit at Third Floor, Seat 42"
├── Directory is always updated (people register when they arrive)
├── You check directory → "Finance is at 3F-42" → go directly
├── If they moved → directory updated → you still find them
├── If they're on leave → removed from directory → you know immediately
└── No time wasted!

IN OUR MICROSERVICES:
├── "Finance" = routing-service
├── "Seat number" = IP address + port (192.168.1.5:8084)
├── "Directory" = Eureka Server
├── "Register" = service tells Eureka its address on startup
├── "Look up" = payment-service asks Eureka where routing-service is
└── "On leave" = service crashes → heartbeat stops → Eureka removes it
```

---

## How Eureka Works (Technical Detail)

```
STARTUP:
1. Eureka Server starts on port 8761 (first thing that starts)
2. routing-service starts on port 8084
3. routing-service sends POST to Eureka: 
   "Hi, I'm ROUTING-SERVICE at 192.168.1.5:8084"
4. Eureka saves: { "ROUTING-SERVICE": ["192.168.1.5:8084"] }
5. payment-service starts, registers similarly
6. All services registered!

HEARTBEAT (every 30 seconds):
1. routing-service sends heartbeat to Eureka: "I'm still alive at 8084"
2. Eureka renews the registration
3. If NO heartbeat for 90 seconds → Eureka assumes service is dead
4. Removes it from registry
5. Other services asking for ROUTING-SERVICE won't get dead address

SERVICE CALL:
1. payment-service needs to call routing-service
2. payment-service asks Eureka: "Where is ROUTING-SERVICE?"
3. Eureka responds: "192.168.1.5:8084" (or multiple addresses if scaled)
4. payment-service calls: http://192.168.1.5:8084/internal/route
5. If there are 3 instances → Eureka returns all 3 → client load-balances

MULTIPLE INSTANCES (scaling):
1. routing-service instance #1: registers as "ROUTING-SERVICE at 192.168.1.5:8084"
2. routing-service instance #2: registers as "ROUTING-SERVICE at 192.168.1.6:8084"
3. Eureka stores: { "ROUTING-SERVICE": ["192.168.1.5:8084", "192.168.1.6:8084"] }
4. payment-service gets BOTH addresses → alternates between them (round-robin)
5. If #1 crashes → heartbeat stops → Eureka removes → only #2 returned
```

---

## Step 2.1: Create pom.xml

**Create file:** `service-registry/pom.xml`

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
        <!-- Inherits: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1 -->
    </parent>

    <artifactId>service-registry</artifactId>
    <name>PayFlow Service Registry</name>
    <description>Eureka Server - Service Discovery for all microservices</description>

    <dependencies>
        <!-- This ONE dependency gives us a fully functional Eureka Server -->
        <!-- Includes: Eureka core, web dashboard, REST API, heartbeat mechanism -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
        <!-- No version needed! Parent POM's spring-cloud BOM manages it -->
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

## Step 2.2: Create Main Application Class

**Create file:** `service-registry/src/main/java/com/payflow/registry/ServiceRegistryApplication.java`

```java
package com.payflow.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
// Standard Spring Boot entry point

@EnableEurekaServer
// THIS ONE ANNOTATION makes this app a fully functional Eureka Server!
// It adds:
//   - Web dashboard at http://localhost:8761
//   - REST API for service registration (POST /eureka/apps/{appName})
//   - Heartbeat receiver (PUT /eureka/apps/{appName}/{instanceId})
//   - Registry query API (GET /eureka/apps)
//   - Self-preservation mode (doesn't panic if many services go away at once)
//
// Without this annotation: it's just a regular Spring Boot app with nothing special.
// With this annotation: it becomes the "phone book" for all other services.

public class ServiceRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
        // Starts embedded Tomcat on port 8761
        // Initializes Eureka server
        // Ready to accept registrations!
    }
}
```

---

## Step 2.3: Create application.yml

**Create file:** `service-registry/src/main/resources/application.yml`

```yaml
# ===== Eureka Server Configuration =====

server:
  port: 8761
  # Standard Eureka port (everyone knows to look at 8761)
  # All other services have: eureka.client.service-url.defaultZone: http://localhost:8761/eureka/

spring:
  application:
    name: service-registry
    # This service's own name (shows in Eureka dashboard)

eureka:
  instance:
    hostname: localhost
    # The hostname this Eureka server identifies itself with

  client:
    register-with-eureka: false
    # FALSE because: THIS is the registry — don't try to register WITH YOURSELF!
    # If true: Eureka would try to register as a client to itself (creates confusion)
    # ALL OTHER SERVICES set this to TRUE (they register with us)

    fetch-registry: false
    # FALSE because: THIS is the source of truth — don't try to FETCH from yourself!
    # ALL OTHER SERVICES set this to TRUE (they download the registry to know where others are)

    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
      # The URL where THIS Eureka server's API is available
      # Other services use this URL to register

  server:
    wait-time-in-ms-when-sync-empty: 0
    # Don't wait for other Eureka instances during startup
    # (We're running single-instance; in production you'd run 2-3 Eureka instances)

    eviction-interval-timer-in-ms: 10000
    # How often to check for dead services (every 10 seconds)
    # If a service hasn't sent heartbeat in 90 seconds → remove it
    # Default is 60000 (60 sec) — we reduce for faster detection in dev
```

---

## Step 2.4: Build and Run

### Build:
```cmd
cd payflow-payment-gateway
mvn clean install -DskipTests -pl service-registry -am
```

**What `-am` means:** "Also Make" dependencies. Since service-registry depends on
the parent POM, Maven builds what's needed first.

**Expected output:**
```
[INFO] --- maven-compiler-plugin:3.11.0:compile ---
[INFO] --- spring-boot-maven-plugin:3.2.5:repackage ---
[INFO] BUILD SUCCESS
[INFO] Total time: 8.xxx s
```

### Run:
```cmd
cd service-registry
mvn spring-boot:run
```

**Console output to look for:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.5)

... (startup logs) ...
Started ServiceRegistryApplication in 6.xxx seconds (process running for 7.xxx)
```

### Open Dashboard:
```
Browser → http://localhost:8761
```

**What you should see:**
```
┌─────────────────────────────────────────────────────────────────┐
│                    EUREKA                                         │
│                                                                   │
│  System Status:                                                   │
│    Environment: test                                              │
│    Current time: 2026-07-20 11:30:00 UTC                         │
│                                                                   │
│  Instances currently registered with Eureka:                      │
│  ──────────────────────────────────────────                       │
│  (none — no services registered yet)                             │
│                                                                   │
│  General Info:                                                    │
│    total-avail-memory: 256mb                                     │
│    num-of-cpus: 4                                                │
│    registered-replicas: (none)                                   │
└─────────────────────────────────────────────────────────────────┘
```

The "Instances currently registered" section is EMPTY because no services 
have registered yet. Once we start identity-service, merchant-service, etc.,
they'll appear here.

---

## Step 2.5: Verify API

Test the Eureka REST API:

```cmd
curl http://localhost:8761/eureka/apps
```

**Expected (XML — empty registry):**
```xml
<applications>
  <versions__delta>1</versions__delta>
  <apps__hashcode></apps__hashcode>
</applications>
```

This means: Eureka is running, API is accessible, but no services registered yet.

---

## Step 2.5.1: Dockerfile (Containerization)

Now that the service-registry works locally, let's understand how we package it into a Docker container. This is the `service-registry/Dockerfile` — read every line and comment carefully.

### What Is a Multi-Stage Build?

```
TRADITIONAL BUILD (single stage):
├── One big image with Maven + JDK + source code + compiled JAR
├── Image size: ~800MB
├── Contains tools you DON'T need at runtime (Maven, compiler, source files)
└── Security risk: more software = more attack surface

MULTI-STAGE BUILD (what we use):
├── Stage 1 (build): Big image with Maven + JDK → compiles the code
├── Stage 2 (runtime): Tiny image with JRE only → runs the JAR
├── Final image size: ~200MB (75% smaller!)
├── Contains ONLY what's needed to run the app
└── Safer: no compiler, no source code, no build tools in production
```

Think of it like a kitchen vs. a restaurant table:
- **Stage 1** is the kitchen — all the tools, ingredients, mess
- **Stage 2** is the table — only the finished dish served to the customer

Docker discards Stage 1 after copying the final JAR to Stage 2. The published image only contains Stage 2.

---

### Complete Dockerfile (with line-by-line explanation)

**File:** `service-registry/Dockerfile`

```dockerfile
# ============================================
# PayFlow Service Registry - Multi-stage Build
# ============================================

# ─── STAGE 1: BUILD WITH MAVEN ───────────────────────────────────
# Use Maven 3.9 with Eclipse Temurin JDK 17 on Alpine Linux
# Alpine = tiny Linux distro (~5MB) → faster download
# This stage exists ONLY to compile our Java code into a JAR
FROM maven:3.9-eclipse-temurin-17-alpine AS build

# Set working directory inside the container
# All subsequent commands run relative to /app
WORKDIR /app

# Copy parent POM first (dependency resolution needs it)
# This layer is CACHED — won't re-run unless pom.xml changes
COPY pom.xml ./pom.xml

# Copy common-lib POM (service-registry depends on common-lib)
COPY common-lib/pom.xml ./common-lib/pom.xml

# Copy common-lib source (needed to compile the dependency)
COPY common-lib/src ./common-lib/src

# Copy service-registry POM (defines THIS module's dependencies)
COPY service-registry/pom.xml ./service-registry/pom.xml

# Download ALL dependencies (cached as a Docker layer!)
# -pl service-registry = only this module
# -am = "also make" dependencies (common-lib)
# -B = batch mode (no interactive prompts)
# WHY separate step? Docker caches this layer. If only source code
# changes (not pom.xml), Docker skips this 2-minute download!
RUN mvn dependency:go-offline -pl service-registry -am -B

# NOW copy the actual source code
# This is AFTER dependency download — so code changes don't
# trigger a full dependency re-download (saves minutes per build)
COPY service-registry/src ./service-registry/src

# Compile and package into a JAR file
# -DskipTests = don't run tests during Docker build (CI handles that)
# -pl service-registry -am = build service-registry + its dependencies
RUN mvn clean package -DskipTests -pl service-registry -am

# ─── STAGE 2: RUNTIME (PRODUCTION IMAGE) ─────────────────────────
# Use ONLY the JRE (Java Runtime Environment) — no compiler needed!
# eclipse-temurin:17-jre-alpine = ~85MB vs JDK image ~350MB
# JRE can RUN Java apps but cannot COMPILE them (that's fine here)
FROM eclipse-temurin:17-jre-alpine

# Working directory for the running application
WORKDIR /app

# Create a non-root user and group called "payflow"
# WHY? Security! If a hacker exploits the app, they get limited
# permissions (payflow user) instead of root access to the container.
# -S = system account (no login shell, no home directory needed)
RUN addgroup -S payflow && adduser -S payflow -G payflow

# Install curl (needed for Docker HEALTHCHECK below)
# --no-cache = don't store the package index (saves ~5MB)
RUN apk add --no-cache curl

# Copy the compiled JAR from Stage 1 (build stage)
# --from=build = "take this file from the 'build' stage above"
# The *.jar glob matches the single JAR file produced by Maven
# Everything else from Stage 1 (Maven, source code, .m2 cache) is DISCARDED
COPY --from=build /app/service-registry/target/*.jar app.jar

# Give the payflow user ownership of the JAR file
RUN chown payflow:payflow app.jar

# Switch to non-root user for all subsequent commands
# From this point, the container runs as "payflow" not "root"
USER payflow

# JVM configuration optimized for Docker containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Tell Docker this container listens on port 8761
# (documentation only — actual port mapping is in docker-compose.yml)
EXPOSE 8761

# Docker health check — Docker automatically monitors this container
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8761/actuator/health || exit 1

# Start the application (shell form for env var expansion)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

### Stage 1 Explained: The Build Stage

```
WHAT HAPPENS IN STAGE 1:

1. Start with maven:3.9-eclipse-temurin-17-alpine image (~400MB)
   └── Contains: Maven 3.9, JDK 17, Alpine Linux

2. Copy POMs first (pom.xml, common-lib/pom.xml, service-registry/pom.xml)
   └── Why first? Docker layer caching!

3. Run mvn dependency:go-offline
   └── Downloads all JARs from Maven Central (~200 dependencies)
   └── Cached! Won't re-run unless pom.xml changes
   └── Saves 2-3 minutes on subsequent builds

4. Copy source code (service-registry/src)
   └── This is the code that changes frequently

5. Run mvn clean package
   └── Compiles .java → .class
   └── Packages into service-registry-1.0.0-SNAPSHOT.jar
   └── Includes all dependencies inside the JAR (fat JAR)

RESULT: /app/service-registry/target/service-registry-1.0.0-SNAPSHOT.jar
```

**Layer caching trick explained:**
```
BUILD #1: (everything runs)
  COPY pom.xml         → new layer (cached)
  RUN mvn dependency   → new layer (2 min, cached)
  COPY src             → new layer (cached)
  RUN mvn package      → new layer (cached)

BUILD #2: (only changed a .java file)
  COPY pom.xml         → cache HIT ✓ (pom didn't change)
  RUN mvn dependency   → cache HIT ✓ (skipped! saves 2 min)
  COPY src             → cache MISS ✗ (source changed)
  RUN mvn package      → must re-run (but dependencies already downloaded)

If we had copied src BEFORE downloading dependencies:
  COPY src + pom.xml   → cache MISS ✗ (source changed)
  RUN mvn dependency   → must re-run (2 min wasted!)
  RUN mvn package      → must re-run
```

---

### Stage 2 Explained: The Runtime Stage

```
WHAT HAPPENS IN STAGE 2:

1. Start with eclipse-temurin:17-jre-alpine (~85MB)
   └── Contains: Java 17 JRE + Alpine Linux
   └── Does NOT contain: Maven, JDK compiler, source code

2. Create non-root user "payflow"
   └── Security: principle of least privilege
   └── If hacker breaks in → can't install software, can't read /etc/shadow

3. Install curl
   └── Needed for: HEALTHCHECK command below
   └── Docker needs to "ask" the container if it's healthy

4. Copy JAR from Stage 1
   └── ONLY the JAR file crosses the stage boundary
   └── Everything else (Maven cache, source, .class files) → gone

5. Set ownership and switch to non-root user
   └── Container process runs as "payflow" user

FINAL IMAGE CONTAINS:
├── Alpine Linux base (~5MB)
├── Java 17 JRE (~80MB)
├── curl (~5MB)
├── app.jar (~45MB)
└── TOTAL: ~135MB (vs ~800MB with a full JDK+Maven image)
```

---

### JVM Flags Explained (JAVA_OPTS)

Each flag in `JAVA_OPTS` optimizes Java for running inside a Docker container:

```
-XX:+UseContainerSupport
│
├── WHAT: Tells the JVM "you're running inside a container"
├── WHY:  Without this, JVM sees the HOST machine's memory (e.g., 16GB)
│         and allocates heap based on that — then gets OOM-killed by Docker
│         because the container only has 512MB!
├── WITH: JVM reads the container's memory limit (512MB) and sizes heap accordingly
└── DEFAULT: Enabled since JDK 10, but explicit here for clarity

-XX:MaxRAMPercentage=75.0
│
├── WHAT: Use 75% of the container's memory for Java heap
├── WHY:  Container has 512MB → JVM heap = 384MB
│         Leaves 128MB for: JVM metaspace, thread stacks, native memory, OS
├── WITHOUT: JVM defaults to 25% → only 128MB heap → frequent garbage collection
├── TOO HIGH (95%): No room for non-heap memory → random crashes
└── SWEET SPOT: 70-80% is recommended for Spring Boot apps

-XX:+UseG1GC
│
├── WHAT: Use the G1 (Garbage-First) garbage collector
├── WHY:  G1 is designed for applications that:
│         • Have heap sizes > 256MB (we have ~384MB)
│         • Need predictable pause times (< 200ms)
│         • Run in containers (respects memory limits)
├── ALTERNATIVE: -XX:+UseZGC (even lower pauses, but uses more memory)
└── DEFAULT: G1 is default in JDK 17, but explicit for documentation

-XX:+HeapDumpOnOutOfMemoryError
│
├── WHAT: If the JVM runs out of memory, write a heap dump file before crashing
├── WHY:  Debugging! The heap dump shows exactly what was using all the memory
│         Without it: app crashes, you have no idea why
│         With it: you get a .hprof file you can analyze with tools like Eclipse MAT
├── FILE: Written to working directory (/app) as java_pid<PID>.hprof
└── PRODUCTION: Essential for post-mortem analysis of OOM crashes

-Djava.security.egd=file:/dev/./urandom
│
├── WHAT: Use /dev/urandom instead of /dev/random for cryptographic operations
├── WHY:  /dev/random can BLOCK if the system doesn't have enough "entropy"
│         (randomness from hardware events like keyboard, mouse, disk)
│         Containers have very little entropy → /dev/random blocks for seconds
│         /dev/urandom never blocks → faster startup, faster session IDs
├── SECURITY: /dev/urandom is cryptographically secure for all practical purposes
│             (the blocking behavior of /dev/random is considered outdated advice)
└── EFFECT: Spring Boot starts 2-5 seconds faster in containers
```

---

### HEALTHCHECK Directive Explained

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8761/actuator/health || exit 1
```

This tells Docker: "here's how to check if this container is actually working."

```
PARAMETERS:

--interval=30s
│  └── Check health every 30 seconds
│      Too frequent (5s) = wasted CPU on health checks
│      Too rare (5min) = slow to detect failures

--timeout=10s
│  └── If the health check takes longer than 10s, consider it failed
│      Prevents hanging health checks from blocking Docker

--start-period=30s
│  └── Wait 30 seconds before starting health checks
│      WHY? JVM startup takes 10-30 seconds (class loading, Spring context)
│      Without start-period: Docker sees "unhealthy" during normal startup
│      and might restart the container in a loop!

--retries=3
│  └── Must fail 3 times IN A ROW before Docker marks it "unhealthy"
│      Protects against temporary blips (GC pause, brief network issue)
│      1 failure = still "healthy" (might be a fluke)
│      3 failures = genuinely broken → Docker marks unhealthy

CMD curl -f http://localhost:8761/actuator/health || exit 1
│  └── The actual health check command
│      curl -f = make HTTP request, fail silently on server errors
│      http://localhost:8761 = inside the container, it's always localhost
│      /actuator/health = Spring Boot Actuator health endpoint
│      || exit 1 = if curl fails → return exit code 1 → unhealthy

CONTAINER STATES:
├── "starting" → within start-period, health checks don't count yet
├── "healthy"  → health check returned exit code 0 (HTTP 200 from actuator)
├── "unhealthy" → failed 3 consecutive checks after start-period
└── Docker doesn't automatically restart unhealthy containers
    (but depends_on with condition: service_healthy uses this!)
```

**How Docker Compose uses HEALTHCHECK:**
```yaml
# In docker-compose.yml:
config-server:
  depends_on:
    service-registry:
      condition: service_healthy  ← waits for HEALTHCHECK to pass!
```

This means: "don't start config-server until service-registry's health check
passes at least once." That's how we enforce startup ordering.

---

### ENTRYPOINT Explained

```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

```
WHY SHELL FORM (sh -c) INSTEAD OF EXEC FORM?

EXEC FORM (doesn't expand variables):
  ENTRYPOINT ["java", "-jar", "app.jar"]
  └── $JAVA_OPTS would NOT be expanded — it's passed literally as a string
  └── JVM receives the text "$JAVA_OPTS" not the actual flags!

SHELL FORM via sh -c (expands variables):
  ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
  └── sh interprets the command string
  └── $JAVA_OPTS is expanded to the full value of the ENV variable
  └── JVM receives: java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 ... -jar app.jar

WE USE sh -c BECAUSE:
├── We defined JAVA_OPTS as an ENV variable
├── We want those flags passed to the java command
├── Environment variable expansion requires a shell
├── This also lets us override JAVA_OPTS at runtime:
│     docker run -e JAVA_OPTS="-Xmx256m" payflow-registry
│     (useful for different environments)
└── Tradeoff: PID 1 is "sh" not "java" — but for this use case it's fine
```

---

### Summary: What the Dockerfile Achieves

```
INPUT:
├── Java source code (service-registry/src)
├── POM files (dependency declarations)
└── Multi-stage Dockerfile (build instructions)

PROCESS:
├── Stage 1: Download deps → Compile → Package JAR
└── Stage 2: Copy JAR → Configure JVM → Set up health check

OUTPUT: A Docker image that:
├── Is ~135MB (not 800MB)
├── Runs as non-root user (secure)
├── Automatically reports health status to Docker
├── Respects container memory limits (won't OOM)
├── Uses efficient garbage collection (G1GC)
├── Starts fast (urandom, no entropy blocking)
└── Produces heap dumps on crashes (debuggable)
```

---

## Step 2.6: What Happens Next (Preview)

When we start identity-service (Phase 4), it will have this config:

```yaml
# In identity-service/application.yml:
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    # "Register me with Eureka at this address"
  instance:
    prefer-ip-address: true
    # "Register my IP address (not hostname)"
```

And identity-service will automatically:
1. On startup: Register with Eureka ("I'm IDENTITY-SERVICE at 192.168.1.5:8081")
2. Every 30 sec: Send heartbeat ("I'm still alive")
3. On shutdown: Deregister ("I'm going away")

Eureka dashboard will then show:
```
Instances currently registered with Eureka:
Application         AMIs    Availability Zones    Status
IDENTITY-SERVICE    n/a     (1)                   UP (1) - 192.168.1.5:8081
```

---

## Step 2.7: Common Errors and Fixes

| Problem | Cause | Fix |
|---------|-------|-----|
| "Port 8761 already in use" | Another process on 8761 | `netstat -ano | findstr 8761` → kill the PID |
| Dashboard shows blank page | Browser cache | Hard refresh (Ctrl+Shift+R) |
| Other services can't register | Eureka not running | Start Eureka FIRST, then other services |
| "Connection refused" in service logs | Eureka not accessible | Check Eureka is running on 8761 |
| Service shows as DOWN | Service crashed after registering | Restart the service |

---

## Step 2.8: Git Commit

```cmd
git add service-registry/
git commit -m "Phase 3 Part 2: Eureka service registry (port 8761, dashboard, service discovery)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `service-registry/pom.xml` | Dependency: spring-cloud-starter-netflix-eureka-server |
| `ServiceRegistryApplication.java` | Main class + @EnableEurekaServer |
| `application.yml` | Port 8761, standalone mode (no self-registration) |

---

## Interview Notes

**Q: "What is service discovery and why do you need it?"**
> "Service discovery eliminates hardcoded URLs between microservices. Each service registers its address with Eureka on startup. When service A needs service B, it asks Eureka for B's current address. This means services can move between servers, scale to multiple instances, or restart on different ports — and the rest of the system adapts automatically without config changes."

**Q: "What happens if Eureka goes down?"**
> "Services cache the registry locally. If Eureka is temporarily unavailable, services continue using their cached copy of the registry. When Eureka comes back, they re-register and get fresh data. In production, we'd run 2-3 Eureka instances for high availability — they replicate the registry between themselves."

**Q: "How does load balancing work with Eureka?"**
> "If multiple instances of a service register (e.g., 3 instances of payment-service), Eureka returns all addresses. Spring Cloud LoadBalancer (integrated with Feign) automatically rotates between them using round-robin. If one instance becomes unhealthy, Eureka removes it from the registry — new requests only go to healthy instances."

---

## Startup Order (Important!)

```
MUST start services in this order:

1. service-registry (port 8761) ← START THIS FIRST
   └── Wait 5 seconds for it to fully boot

2. config-server (port 8888)
   └── Registers with Eureka, provides config to other services

3. api-gateway (port 8080)
   └── Registers with Eureka, routes traffic

4. Business services (identity, merchant, payment, routing, etc.)
   └── All register with Eureka, fetch config from config-server

WHY THIS ORDER?
├── Services need Eureka to register → Eureka must be first
├── Services need config → Config Server must be second  
├── Gateway routes to services → must know about them → start after
└── Business services depend on all of the above
```

---

## Next Step

→ Continue to **Phase 3 Part 3: Config Server**
