# Hands-On Guide — Phase 7 Part 1: Routing Service — Project Setup

## Goal

By the end of Part 1, you will have:
- routing-service Maven module with dependencies (web, Redis, Netty, Eureka)
- Application class with Swagger config
- application.yml with bank simulator connection details
- Service starts on port 8084
- Understanding of what routing-service does in the architecture
- Git commit

## Prerequisites

- Phase 6 completed (payment-service working end-to-end)
- Docker running (Redis needed for route metrics caching)

---

## What Does the Routing Service Do?

```
PAYMENT SERVICE says: "I have a card payment for ₹5000. Process it."
ROUTING SERVICE:
  1. DECIDE: Which bank should handle this? (HDFC? ICICI? Axis?)
     └── AI scoring: success rate, cost, latency per bank
  2. BUILD: ISO 8583 message (binary format banks understand)
     └── Pack card number, amount, merchant into bytes
  3. SEND: Via TCP socket to the bank
     └── Open connection to bank simulator (port 9000)
  4. RECEIVE: Bank's response (approve/decline)
     └── Parse ISO 8583 response, extract auth code
  5. RETURN: Result to payment-service
     └── { success: true, authCode: "A1B2C3", rrn: "987654321012" }

WHY A SEPARATE SERVICE?
├── Payment service handles business logic (state machine, idempotency)
├── Routing service handles bank communication (protocol, TCP, failover)
├── If we add a new bank → only routing-service changes
├── If bank protocol changes → only routing-service changes
└── Payment service doesn't know/care HOW the bank is reached
```

---

## Step 1.1: Create pom.xml

**Create file:** `routing-service/pom.xml`

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

    <artifactId>routing-service</artifactId>
    <name>PayFlow Routing Service</name>
    <description>Smart payment routing + ISO 8583 bank communication</description>

    <dependencies>
        <!-- Web: REST controller (receives routing requests from payment-service) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Redis: Cache route metrics (success rates, latency per bank) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Eureka: Register with service discovery -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- Swagger UI -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>

        <!-- Netty: High-performance TCP client for bank communication -->
        <!-- We use plain Java sockets for simplicity, but Netty is available -->
        <!-- for connection pooling and async I/O if needed later -->
        <dependency>
            <groupId>io.netty</groupId>
            <artifactId>netty-all</artifactId>
        </dependency>

        <!-- Our common library -->
        <dependency>
            <groupId>com.payflow</groupId>
            <artifactId>common-lib</artifactId>
        </dependency>

        <!-- Health checks -->
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

---

## Step 1.2: Create Application Class

**Create file:** `routing-service/src/main/java/com/payflow/routing/RoutingServiceApplication.java`

```java
package com.payflow.routing;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.routing", "com.payflow.common"})
@OpenAPIDefinition(info = @Info(
        title = "PayFlow Routing Service API",
        version = "1.0",
        description = "Smart payment routing engine + ISO 8583 bank communication. "
            + "Internal API: called by payment-service, not directly by merchants."
))
// This is an INTERNAL service (not exposed through API Gateway to merchants)
// Only payment-service calls this via Feign
public class RoutingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoutingServiceApplication.class, args);
    }
}
```

---

## Step 1.3: Create application.yml

**Create file:** `routing-service/src/main/resources/application.yml`

```yaml
server:
  port: 8084
  # Routing service on port 8084
  # INTERNAL service — not exposed through API Gateway

spring:
  application:
    name: routing-service
    # Registers as "ROUTING-SERVICE" in Eureka
    # Payment service calls: @FeignClient(name = "ROUTING-SERVICE")

  data:
    redis:
      host: localhost
      port: 6379
      # Redis stores route metrics:
      # - Success rate per bank (last 1 hour)
      # - Average latency per bank
      # - Failure count per bank
      # Used by routing engine to pick best bank

# Bank Simulator Connection
bank:
  simulator:
    host: localhost
    port: 9000
    # Bank simulator listens on TCP port 9000
    # In production: this would be Visa/MC/NPCI network endpoint
    timeout-ms: 5000
    # If bank doesn't respond in 5 seconds → timeout → send reversal

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
```

---

## Step 1.4: Verify

### Build:
```cmd
mvn clean install -DskipTests -pl common-lib,routing-service -am
```

### Run:
```cmd
cd routing-service
mvn spring-boot:run
```

### Check:
- Console: `Started RoutingServiceApplication in X.xxx seconds`
- Swagger: http://localhost:8084/swagger-ui.html
- Eureka: http://localhost:8761 shows ROUTING-SERVICE registered

---

## Step 1.5: Git Commit

```cmd
git add routing-service/pom.xml
git add routing-service/src/main/java/com/payflow/routing/RoutingServiceApplication.java
git add routing-service/src/main/resources/application.yml
git commit -m "Phase 7 Part 1: Routing service setup - pom, application class, config"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `routing-service/pom.xml` | Dependencies (web, Redis, Netty, Eureka) |
| `RoutingServiceApplication.java` | Main class + Swagger config |
| `application.yml` | Port 8084, bank simulator host:port, Redis, Eureka |

---

## Project Structure After This Part

```
routing-service/
├── pom.xml
└── src/main/
    ├── java/com/payflow/routing/
    │   ├── RoutingServiceApplication.java     ← Done (this part)
    │   ├── iso8583/                           ← Part 4-5 (message classes, encoder, decoder)
    │   ├── service/                           ← Part 2-3 (routing engine, TCP client)
    │   ├── dto/                               ← Part 2 (RouteRequest, RouteResponse)
    │   └── controller/                        ← Part 9 (RoutingController)
    └── resources/
        └── application.yml                    ← Done (this part)
```

---

## Next Step

→ Continue to **Phase 7 Part 2: Smart Routing Engine**
