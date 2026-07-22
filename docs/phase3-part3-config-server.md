# Phase 3 — Part 3: Config Server (Centralized Configuration)

> In this part, we create the Spring Cloud Config Server.
> It stores configuration for ALL services in one place.
> After this: services fetch their DB URLs, secrets, ports from here.

---

## 1. What Is Config Server? (Concept)

**Problem:**
```
WITHOUT Config Server:
├── identity-service has application.yml with DB password
├── payment-service has application.yml with SAME DB password
├── merchant-service has application.yml with SAME DB password
├── ... (7 more services with same DB password)
│
├── Database password changes → edit 10 files → redeploy 10 services 😩
└── Want different config for dev/staging/prod → maintain 30 config files 😱
```

**Solution:**
```
WITH Config Server:
├── ONE folder: config-server/configurations/
│   ├── identity-service.yml (port, DB, JWT settings)
│   ├── payment-service.yml (port, DB, Redis, circuit breaker)
│   └── ... (one file per service)
│
├── Database password changes → edit ONE file → services auto-refresh ✅
└── Different environments → just different profiles (dev, prod) ✅
```

**How it works step by step:**
```
1. Config Server starts on port 8888
2. Config files are in: config-server/src/main/resources/configurations/
3. identity-service starts
4. identity-service calls: GET http://config-server:8888/identity-service/default
5. Config Server reads configurations/identity-service.yml
6. Returns content as JSON
7. identity-service uses those settings (port 8081, DB URL, JWT secret, etc.)
```

---

## 2. Project Structure

```
config-server/
├── pom.xml
└── src/main/
    ├── java/com/payflow/config/
    │   └── ConfigServerApplication.java
    └── resources/
        ├── application.yml                    ← Config server's OWN config
        └── configurations/                    ← Config files FOR other services
            ├── identity-service.yml
            ├── merchant-service.yml
            ├── payment-service.yml
            ├── routing-service.yml
            ├── settlement-service.yml
            ├── webhook-service.yml
            └── notification-service.yml
```

---

## 3. Code: ConfigServerApplication.java

```java
@SpringBootApplication
@EnableConfigServer   // ← This ONE annotation makes it a config server
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

---

## 4. Code: application.yml (Config Server's Own Config)

```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  profiles:
    active: native        # Read config from local filesystem (not Git)
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/configurations

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

**Key setting: `profiles.active: native`**
- `native` = read config files from local folder (classpath:/configurations)
- Alternative: `git` = read from a Git repository (used in production)
- For our project, `native` is simpler and works great

---

## 5. Service Configuration Files

These files are what Config Server SERVES to each service.

### 5.1 identity-service.yml

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=identity
    username: payflow
    password: payflow_secret
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: identity
  flyway:
    enabled: true
    schemas: identity

jwt:
  secret: payflow-jwt-secret-key-change-in-production-minimum-256-bits
  access-token-expiry: 900000       # 15 minutes
  refresh-token-expiry: 604800000   # 7 days

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### 5.2 payment-service.yml

```yaml
server:
  port: 8083

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=payment
    username: payflow
    password: payflow_secret
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: payment
  flyway:
    enabled: true
    schemas: payment
  data:
    redis:
      host: localhost
      port: 6379

payment:
  order-expiry-minutes: 30
  auth-expiry-days: 7
  idempotency-key-ttl-hours: 24

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

resilience4j:
  circuitbreaker:
    instances:
      routing-service:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

### 5.3 How Services Fetch Config

Each service has a minimal `bootstrap.yml` (or uses `spring.config.import`):

```yaml
# In each service's application.yml:
spring:
  application:
    name: identity-service     # MUST match filename in configurations/
  config:
    import: optional:configserver:http://localhost:8888
```

When identity-service starts:
1. Reads its own application.yml (just the name + config server URL)
2. Calls Config Server: `GET http://localhost:8888/identity-service/default`
3. Receives the full configuration (port, DB, JWT, Eureka settings)
4. Applies it all

---

## 6. How to Run & Verify

### Step 1: Start Eureka first (Config Server registers with it)
```cmd
cd service-registry
mvn spring-boot:run
```

### Step 2: Start Config Server
```cmd
cd config-server
mvn spring-boot:run
```

### Step 3: Verify Config Server serves configuration

```cmd
curl http://localhost:8888/identity-service/default
```

**Expected response (JSON):**
```json
{
  "name": "identity-service",
  "profiles": ["default"],
  "propertySources": [
    {
      "name": "classpath:/configurations/identity-service.yml",
      "source": {
        "server.port": 8081,
        "spring.datasource.url": "jdbc:postgresql://localhost:5432/payflow?currentSchema=identity",
        "jwt.secret": "payflow-jwt-secret-key...",
        ...
      }
    }
  ]
}
```

If you see this JSON → Config Server is working correctly!

### Step 4: Check Eureka

Open http://localhost:8761 → you should see CONFIG-SERVER registered.

---

## 7. Startup Order (IMPORTANT)

Services MUST start in this order:

```
1. service-registry (Eureka) — port 8761
   ↓ (wait 5 seconds)
2. config-server — port 8888
   ↓ (wait 5 seconds)
3. All other services (identity, payment, merchant, etc.)
```

**Why this order?**
- Other services need Config Server to get their configuration
- Config Server needs Eureka to register itself
- So Eureka must be first

---

## 8. What If Config Server Is Down?

```
Scenario: Config Server crashes after services already started.

Result: Running services CONTINUE working (they cached config at startup).
Only NEW services starting up will fail (can't fetch config).

Solution for production:
- Run 2+ instances of Config Server behind a load balancer
- Or use fallback: each service has default config in its own application.yml
```

---

## 9. Interview Notes

**Q: "Why centralized configuration?"**
> "To avoid duplicating config across 11 services. Database URLs, secrets, and feature flags are managed in one place. When a password changes, I update one file instead of 11."

**Q: "How do services get their config?"**
> "Each service has a `spring.config.import` that points to Config Server (http://localhost:8888). On startup, it fetches its named config file. Config Server reads from a local folder (or Git in production)."

**Q: "Can you change config without restarting services?"**
> "Yes, with Spring Cloud Bus + RabbitMQ/Kafka, we can push config changes to running services. For our project, we restart services after config changes (simpler)."

---

## Next Step

→ Continue to **`phase3-part4-api-gateway.md`**

In Part 4, we create the API Gateway — the single entry point for all requests.
