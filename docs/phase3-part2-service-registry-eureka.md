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
