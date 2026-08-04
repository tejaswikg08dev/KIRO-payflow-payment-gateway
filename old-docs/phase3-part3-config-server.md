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
They live in `config-server/src/main/resources/configurations/` and are named to match
each service's `spring.application.name`.

### 5.1 identity-service.yml — Deep Walkthrough

Here's the complete file with line-by-line explanation of every section:

```yaml
# ──────────────────────────────────────────────────────────────────
# Configuration for Identity Service
# This file is served by Config Server when identity-service starts up
# ──────────────────────────────────────────────────────────────────

server:
  port: 8081
```

**`server.port: 8081` — Port Allocation Strategy**

Each microservice gets its own fixed port. Our allocation scheme:
| Service | Port |
|---------|------|
| service-registry (Eureka) | 8761 |
| config-server | 8888 |
| api-gateway | 8080 |
| identity-service | 8081 |
| merchant-service | 8082 |
| payment-service | 8083 |

Why 8081 for identity? No hard rule — just needs to be unique per service.
In Docker, each container has its own network namespace so ports *could* overlap,
but unique ports make local development (running outside Docker) much easier.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payflow?currentSchema=identity
    username: payflow
    password: payflow_secret
    driver-class-name: org.postgresql.Driver
```

**`spring.datasource.url` — JDBC URL Breakdown**

Let's decompose this URL piece by piece:
```
jdbc:postgresql://  → JDBC driver prefix (tells Java to use PostgreSQL driver)
localhost:5432      → Database host and port (default PostgreSQL port)
/payflow            → The DATABASE name (all services share ONE database)
?currentSchema=identity  → The SCHEMA within that database
```

**What are schemas?**
Think of a PostgreSQL database as a building, and schemas as rooms inside it:
```
payflow (database = the building)
├── identity (schema = room 1)  → users, roles, refresh_tokens tables
├── merchant (schema = room 2)  → merchants, api_keys tables
├── payment (schema = room 3)   → payments, transactions tables
└── public  (schema = default)  → (we don't use this)
```

Why share one database but use separate schemas?
- **Simpler infrastructure:** One PostgreSQL instance to manage, back up, monitor
- **Data isolation:** Each service only sees its own tables (can't accidentally query another service's data)
- **Easy local dev:** One DB connection, multiple logical separations
- **Production path:** Can migrate to separate databases later if needed (just change the URL)

The `?currentSchema=identity` query parameter tells the JDBC driver: "When I run SQL without specifying a schema, use `identity`."

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: identity
```

**`hibernate.ddl-auto: validate` — Why NOT `update` or `create`?**

Hibernate's `ddl-auto` options:
| Value | What it does | When to use |
|-------|-------------|-------------|
| `create` | Drops all tables, recreates from entities | Never in real projects (data loss!) |
| `create-drop` | Creates on start, drops on shutdown | Unit tests only |
| `update` | Tries to add new columns/tables | Dangerous — never removes columns, can corrupt data |
| `validate` | Just CHECKS if tables match entities | **Production-safe ✅** |
| `none` | Does nothing | When you handle schema yourself |

We use `validate` because **Flyway handles all schema changes** (see below).
Hibernate just checks: "Do the @Entity classes match what's in the database?"
If they don't match → application fails to start → you know something's wrong.

**`hibernate.default_schema: identity`**

This is a belt-and-suspenders approach with the JDBC URL's `?currentSchema=identity`.
It tells Hibernate: "When generating SQL, prefix tables with `identity.`"
```sql
-- Without default_schema: SELECT * FROM users
-- With default_schema:    SELECT * FROM identity.users
```

Both the JDBC parameter and this property achieve the same goal.
Having both ensures isolation no matter which code path generates the SQL.

```yaml
  flyway:
    enabled: true
    schemas: identity
    locations: classpath:db/migration
```

**What is Flyway?**

Flyway is a **database migration tool**. Think of it as "version control for your database schema."

Without Flyway:
```
Developer A: "I added a column to the users table"
Developer B: "Which column? I don't have it"
Production:  "Nobody ran the ALTER TABLE... app is crashing"
```

With Flyway:
```
db/migration/
├── V1__create_users_table.sql       ← Version 1
├── V2__add_email_verified.sql       ← Version 2
└── V3__add_refresh_tokens_table.sql ← Version 3
```

When the app starts, Flyway checks: "Which versions have already been applied?"
Then it runs only the NEW ones, in order. Every developer and every environment
gets the exact same schema.

**`schemas: identity`** — Tells Flyway: "Run migrations inside the `identity` schema."
This is an array internally — you could write `schemas: [identity, shared]` if you
needed to manage multiple schemas, but one per service is the clean pattern.

**`locations: classpath:db/migration`** — Where to find the `.sql` files.
These live in `identity-service/src/main/resources/db/migration/`.

```yaml
# JWT Configuration
jwt:
  secret: payflow-jwt-secret-key-change-in-production-minimum-256-bits-long
  access-token-expiry: 900000       # 15 minutes in milliseconds
  refresh-token-expiry: 604800000   # 7 days in milliseconds
```

**`jwt.secret` — Why Minimum 256 Bits?**

JWT tokens are signed with HMAC-SHA256 (HS256). The "256" means the algorithm
works with a 256-bit key. If your secret is shorter than 256 bits (32 bytes),
the library will either:
- **Reject it** — throws an exception on startup ("key must be at least 256 bits")
- **Pad it** — silently weaker security

Our secret string is 58 characters. Since each ASCII character = 8 bits,
that's 464 bits — well above the 256-bit minimum. ✅

In production, this would be a random 256+ bit key stored in a vault,
not a human-readable string in a config file.

**`access-token-expiry` vs `refresh-token-expiry` — Security Trade-off**

```
Access Token:  900000 ms = 15 minutes (SHORT-lived)
Refresh Token: 604800000 ms = 7 days (LONG-lived)
```

Why are they different? It's a security-vs-convenience trade-off:

```
┌─────────────────────────────────────────────────────────────┐
│ Access Token (15 min)                                        │
│ • Sent with EVERY API request (in Authorization header)      │
│ • If stolen → attacker has 15 minutes max                    │
│ • Expires quickly → user barely notices                      │
│                                                              │
│ Refresh Token (7 days)                                       │
│ • Only sent to ONE endpoint: /auth/refresh                   │
│ • Used to get a NEW access token when the old one expires    │
│ • If stolen → attacker has 7 days (but we can revoke it)     │
│ • Stored in DB → we can invalidate it server-side            │
└─────────────────────────────────────────────────────────────┘
```

The flow:
1. User logs in → gets access token (15 min) + refresh token (7 days)
2. User makes API calls → sends access token
3. After 15 min → access token expires → 401 Unauthorized
4. Client sends refresh token → gets new access token
5. After 7 days → refresh token expires → user must log in again

Short access tokens limit damage from token theft.
Long refresh tokens mean users don't have to log in every 15 minutes.

```yaml
# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

**`eureka.client.service-url.defaultZone`**

This tells the identity-service: "Register yourself with Eureka at this URL."
When the service starts, it sends a REST call to Eureka saying:
"Hey, I'm `identity-service`, I'm running at 192.168.1.5:8081, I'm healthy."

In Docker mode, this gets overridden by an environment variable:
```
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://service-registry:8761/eureka/
```
(Spring Boot's relaxed binding converts the env var name to the YAML path)

**`eureka.instance.prefer-ip-address: true` — Docker vs Dev Machine**

| Setting | What Eureka advertises | Best for |
|---------|----------------------|----------|
| `prefer-ip-address: true` | `192.168.1.5:8081` | Docker / cloud / multiple machines |
| `prefer-ip-address: false` | `my-laptop-hostname:8081` | Single developer on one machine |

In Docker, containers have names like `a7f3b2c1d4e5` — not useful for routing.
With `prefer-ip-address: true`, Eureka stores the container's IP on the Docker
network (e.g., `172.18.0.5`), which other containers CAN reach.

With `false`, Eureka would store the container's hostname, which might not
be resolvable by other containers.

```yaml
# Swagger
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

These expose API documentation. Not critical for the config discussion,
but they let you browse the identity-service REST API at `http://localhost:8081/swagger-ui.html`.

---

### 5.2 payment-service.yml — Deep Walkthrough

Payment service has everything identity has, PLUS Redis, business rules, and circuit breakers.
Here's the complete file explained:

```yaml
server:
  port: 8083
```

Port 8083 — each service gets its own. (See port table in 5.1 above.)

```yaml
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
```

Same pattern as identity, but notice `?currentSchema=payment` and `schemas: payment`.
Same database (`payflow`), different room (`payment` schema).
Payment service's tables (payments, transactions, etc.) live in the `payment` schema,
completely isolated from identity's tables.

```yaml
  data:
    redis:
      host: localhost
      port: 6379
```

**Why Does Payment Service Need Redis?**

Identity service doesn't have Redis. Payment service does. Why?

Payment processing has specific needs that Redis solves:

1. **Idempotency Keys** — Preventing duplicate charges
   ```
   Customer clicks "Pay" twice → Two requests hit the server
   Without Redis: Two charges on their card 😱
   With Redis: Store idempotency key → second request returns cached result ✅
   ```

2. **Caching** — Frequently accessed data (exchange rates, merchant configs)
   ```
   Every payment checks merchant config → hitting DB every time is slow
   Cache in Redis → sub-millisecond lookups
   ```

3. **Rate limiting** — Per-merchant transaction throttling
   ```
   Redis INCR + EXPIRE → count requests per time window
   ```

Redis is an in-memory key-value store. It's fast (microseconds per operation)
and perfect for data that's accessed frequently but can be regenerated if lost.

```yaml
# Payment Configuration
payment:
  order-expiry-minutes: 30
  auth-expiry-days: 7
  idempotency-key-ttl-hours: 24
```

**`order-expiry-minutes: 30` — Business Rule: Payment Window**

When a customer initiates a payment, they have 30 minutes to complete it.
After 30 minutes, the payment order expires and they'd need to start over.

Why 30 minutes?
- Too short (5 min) → customers get frustrated if they're entering card details
- Too long (24 hours) → prices/inventory could change, fraud risk increases
- 30 minutes → enough time to complete, short enough to be safe

**`auth-expiry-days: 7` — Authorization Hold Duration**

In credit card processing, an "authorization" is a HOLD on funds — the money
isn't taken yet, it's just reserved. Think of it like a hotel holding your
card for incidentals.

Why 7 days?
- Card networks (Visa, Mastercard) typically allow holds for 7–30 days
- After 7 days, uncaptured authorizations are automatically released
- This gives merchants time to ship goods before capturing the payment
- Going beyond 7 days risks the auth being voided by the issuing bank

**`idempotency-key-ttl-hours: 24` — Preventing Duplicate Charges**

An idempotency key is a unique ID that the client sends with a payment request.
If the same key comes in twice, we return the same response (no double charge).

```
Request 1: POST /payments  (Idempotency-Key: abc-123) → Process → 200 OK
Request 2: POST /payments  (Idempotency-Key: abc-123) → "Already processed" → 200 OK (cached)
```

Why store for 24 hours?
- Retries typically happen within seconds/minutes (network timeouts, user double-clicks)
- 24 hours covers edge cases (batch retries, delayed webhooks)
- After 24 hours, the key expires from Redis (memory cleanup)
- If somehow the same key comes after 24h, it would process as new — acceptable trade-off

```yaml
# Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

Same Eureka config as identity — registers with the service registry.
(See Section 5.1 for the detailed explanation.)

```yaml
# Resilience4j — Circuit breaker for routing-service calls
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

**What Is a Circuit Breaker?**

A circuit breaker is a pattern borrowed from electrical engineering.
In your house, if too much current flows through a wire, a circuit breaker TRIPS
to prevent a fire. In microservices, if too many requests to a downstream service
are failing, the circuit breaker TRIPS to prevent cascading failures.

Without circuit breaker:
```
payment-service → routing-service (DOWN)
  → request hangs for 30 seconds (timeout)
  → thread is blocked
  → 100 requests pile up → 100 blocked threads
  → payment-service runs out of threads → ALSO goes down
  → cascade failure across the system 💥
```

With circuit breaker:
```
payment-service → routing-service (DOWN)
  → First few requests fail (circuit CLOSED, monitoring)
  → After 50% failure rate → circuit OPENS
  → All subsequent requests immediately fail (no waiting!)
  → After 30 seconds → circuit goes HALF-OPEN (test with 3 calls)
  → If tests pass → circuit CLOSES (back to normal)
  → If tests fail → circuit OPENS again (wait more)
```

**Circuit Breaker State Machine:**
```
┌──────────┐   failure rate     ┌──────────┐   wait 30s    ┌───────────┐
│  CLOSED  │ ──exceeds 50%──►  │   OPEN   │ ──────────►  │ HALF-OPEN │
│ (normal) │                    │(fail-fast)│               │  (testing) │
└──────────┘                    └──────────┘               └───────────┘
     ▲                                                          │
     │              tests pass (< 50% failure)                  │
     └──────────────────────────────────────────────────────────┘
                    tests fail → back to OPEN
```

**Line-by-line settings explained:**

| Setting | Value | Meaning |
|---------|-------|---------|
| `sliding-window-size` | 10 | Look at the LAST 10 calls to decide if we should trip |
| `failure-rate-threshold` | 50 | If 50% (5 out of 10) fail → open the circuit |
| `wait-duration-in-open-state` | 30s | When circuit is OPEN, wait 30 seconds before trying again |
| `permitted-number-of-calls-in-half-open-state` | 3 | When testing (half-open), allow exactly 3 test calls through |

Example scenario:
1. Calls 1-7 to routing-service succeed ✅
2. Calls 8-10 fail ❌ (routing-service went down)
3. Window: 7 success + 3 failures = 30% failure rate → still below 50% → circuit stays CLOSED
4. Calls 11-12 also fail ❌
5. Window (last 10): calls 3-12 → 5 failures = 50% → **CIRCUIT OPENS** 🔴
6. All requests immediately get error response (no waiting for timeout)
7. After 30 seconds → circuit goes HALF-OPEN 🟡
8. 3 test calls are allowed through...
9. If 2+ of 3 pass → circuit CLOSES 🟢 (back to normal)

**Retry settings:**

```yaml
  retry:
    instances:
      routing-service:
        max-attempts: 2
        wait-duration: 200ms
```

| Setting | Value | Meaning |
|---------|-------|---------|
| `max-attempts` | 2 | Try the call up to 2 times total (1 original + 1 retry) |
| `wait-duration` | 200ms | Wait 200 milliseconds between attempts |

Retries happen BEFORE the circuit breaker counts a failure.
If the first attempt fails but the retry succeeds → no failure recorded.
Only if BOTH attempts fail → circuit breaker counts it as one failure.

Why only 2 attempts? Payments are time-sensitive. Retrying 5 times with
200ms waits = 1 second of latency. The customer is staring at a spinner.
2 attempts balances reliability vs user experience.

---

### 5.3 How Services Fetch Config (spring.config.import)

Each service has a minimal `application.yml` of its own with just two things:
its name and where to find Config Server.

```yaml
# In each service's OWN application.yml:
spring:
  application:
    name: identity-service     # ← MUST match filename in configurations/
  config:
    import: optional:configserver:http://localhost:8888
```

**How `spring.config.import` works:**

Let's break down `optional:configserver:http://localhost:8888`:

| Part | Meaning |
|------|---------|
| `optional:` | If Config Server is unreachable, don't crash — use local defaults |
| `configserver:` | Use the Spring Cloud Config client to fetch config |
| `http://localhost:8888` | The URL of the Config Server |

Without `optional:`, if Config Server is down when the service starts, the
service would immediately crash with a connection error. With `optional:`,
it falls back to whatever defaults are in its own `application.yml`.

**The fetch sequence when identity-service starts:**

```
1. Spring Boot starts → reads its own application.yml
2. Sees spring.config.import = configserver:http://localhost:8888
3. Takes spring.application.name = "identity-service"
4. Makes HTTP call: GET http://localhost:8888/identity-service/default
5. Config Server finds: configurations/identity-service.yml
6. Returns the config as JSON
7. Spring Boot merges it with local config (remote takes priority)
8. Application finishes starting with the merged config
```

### 5.4 The Config Server HTTP Endpoint Pattern

Config Server exposes configuration via a simple REST API:

```
GET /{application}/{profile}
GET /{application}/{profile}/{label}
```

| Placeholder | What it means | Example |
|-------------|---------------|---------|
| `{application}` | The service name (matches filename) | `identity-service` |
| `{profile}` | The Spring profile (default, docker, prod) | `default` |
| `{label}` | Git branch (only for git-backed) | `main` |

**Examples:**
```bash
# Get identity-service config (default profile)
GET http://localhost:8888/identity-service/default

# Get payment-service config for docker profile
GET http://localhost:8888/payment-service/docker

# How Spring resolves the file:
#   {application} = "identity-service"
#   Looks for: configurations/identity-service.yml
#   Also looks for: configurations/identity-service-{profile}.yml
#   Merges them (profile-specific overrides default)
```

**Profile resolution order (most specific wins):**
```
configurations/identity-service.yml          ← base config (always loaded)
configurations/identity-service-docker.yml   ← profile-specific (overrides base)
```

So if you have `identity-service.yml` with `server.port: 8081` and
`identity-service-docker.yml` with `server.port: 9081`, the Docker profile
would use port 9081.

### 5.5 Native Profile vs Git-Backed Profile

Config Server supports multiple backends for storing config files:

| Mode | Where configs live | Best for |
|------|-------------------|----------|
| **Native** (our choice) | Local filesystem / classpath | Development, simple projects, learning |
| **Git-backed** | Git repository (GitHub, GitLab) | Production (audit trail, version history) |

**Our project uses `native`:**
```yaml
spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/configurations
```

Why native for this project?
- Simpler setup (no Git repo needed)
- Config files are bundled WITH the Config Server JAR
- Perfect for local development and learning
- Downside: changing config requires rebuilding the Config Server

**When to use Git-backed (production):**
```yaml
spring:
  profiles:
    active: git
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/payflow-config
          default-label: main
```

Git-backed advantages:
- **Audit trail** — who changed what config, when, and why (Git history)
- **No rebuild needed** — push to Git → Config Server picks it up
- **Branch per environment** — main=prod, develop=staging
- **Rollback** — just revert the Git commit

---

## 6. Config Server Dockerfile — Complete Walkthrough

The Config Server has its own Dockerfile for containerization.
It follows the same multi-stage build pattern as service-registry,
but builds the `config-server` module instead.

### 6.1 The Complete Dockerfile

```dockerfile
# ============================================
# PayFlow Config Server - Multi-stage Build
# ============================================

# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy parent POM and common-lib for dependency resolution
COPY pom.xml ./pom.xml
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
COPY config-server/pom.xml ./config-server/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl config-server -am -B

# Copy source code
COPY config-server/src ./config-server/src

# Build the application
RUN mvn clean package -DskipTests -pl config-server -am

# Stage 2: Runtime with JRE only
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S payflow && adduser -S payflow -G payflow

# Install curl for health checks
RUN apk add --no-cache curl

# Copy artifact from build stage
COPY --from=build /app/config-server/target/*.jar app.jar

# Set ownership
RUN chown payflow:payflow app.jar
USER payflow

# JVM configuration for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 6.2 Multi-Stage Build Concept

A multi-stage build uses MULTIPLE `FROM` statements in one Dockerfile.
Each `FROM` creates a separate "stage." Only the LAST stage becomes the final image.

```
┌─────────────────────────────────────────────────────────────────┐
│ Stage 1: BUILD                                                   │
│ Image: maven:3.9-eclipse-temurin-17-alpine (~400 MB)            │
│ Contains: JDK + Maven + all source code + all dependencies      │
│ Purpose: Compile Java source → produce JAR file                  │
│ What happens to this stage: DISCARDED (not in final image)       │
└─────────────────────────────────────────────────────────────────┘
                         │
                         │ COPY --from=build (just the JAR file)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ Stage 2: RUNTIME                                                 │
│ Image: eclipse-temurin:17-jre-alpine (~130 MB)                  │
│ Contains: JRE only + our JAR file + curl                         │
│ Purpose: Run the application                                     │
│ This IS the final image                                          │
└─────────────────────────────────────────────────────────────────┘
```

Why multi-stage?
- **Smaller final image** — JRE (~130MB) vs JDK+Maven (~400MB)
- **Security** — No compiler, no Maven, no source code in production image
- **Build reproducibility** — Anyone with Docker can build it (no local Maven required)

### 6.3 Stage 1: Build Stage — Line by Line

```dockerfile
FROM maven:3.9-eclipse-temurin-17-alpine AS build
```
- Base image: Maven 3.9 + JDK 17 on Alpine Linux (small base)
- `AS build` — names this stage so we can reference it later with `COPY --from=build`

```dockerfile
WORKDIR /app
```
- All subsequent commands run inside `/app` in the container

```dockerfile
# Copy parent POM and common-lib for dependency resolution
COPY pom.xml ./pom.xml
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
COPY config-server/pom.xml ./config-server/pom.xml
```

**Why copy common-lib? (Dependency Resolution)**

This is the key question. Config-server depends on common-lib (it's in the parent POM's modules).
When Maven builds config-server, it needs to resolve this local dependency.

```
Parent POM says: modules = [common-lib, config-server, ...]
config-server/pom.xml says: <dependency>common-lib</dependency>

Maven needs common-lib's POM + source to compile config-server.
Without it: "Could not resolve artifact com.payflow:common-lib:jar:1.0.0"
```

We copy the POMs first (before the full source) for **Docker layer caching**:
- POMs change rarely → this layer gets cached
- Source code changes frequently → only later layers rebuild
- Result: faster rebuilds (Maven doesn't re-download all dependencies)

```dockerfile
# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl config-server -am -B
```

| Flag | Meaning |
|------|---------|
| `dependency:go-offline` | Download ALL dependencies to local Maven repo |
| `-pl config-server` | Only build the config-server module (**p**roject **l**ist) |
| `-am` | **A**lso **m**ake dependencies (builds common-lib too) |
| `-B` | **B**atch mode (non-interactive, no download progress bars) |

This is a **caching optimization**. Since this RUN command only depends on the POM files
(which were copied in the previous step), Docker caches this layer.
Next time you change Java source code, Docker skips re-downloading dependencies!

```dockerfile
# Copy source code
COPY config-server/src ./config-server/src
```

Now copy the actual Java source files. This comes AFTER dependency download
so that source code changes don't invalidate the dependency cache layer.

```dockerfile
# Build the application
RUN mvn clean package -DskipTests -pl config-server -am
```

| Flag | Meaning |
|------|---------|
| `clean package` | Remove old build artifacts, compile, run, and package as JAR |
| `-DskipTests` | Skip unit tests (we ran them in CI, not in Docker build) |
| `-pl config-server -am` | Build config-server + its dependency (common-lib) |

Output: `/app/config-server/target/config-server-1.0.0.jar`

### 6.4 Stage 2: Runtime Stage — Line by Line

```dockerfile
FROM eclipse-temurin:17-jre-alpine
```

A fresh image — only JRE (no JDK, no Maven). Alpine-based = tiny footprint.
Nothing from Stage 1 is present here unless we explicitly COPY it.

```dockerfile
WORKDIR /app
```

```dockerfile
# Create non-root user
RUN addgroup -S payflow && adduser -S payflow -G payflow
```

**Why a non-root user? (Security)**

By default, containers run as `root`. If an attacker exploits a vulnerability
in your Java app, they'd have root access inside the container. With a
non-root user (`payflow`), even if they break in, they can't:
- Install packages
- Modify system files
- Escape to the host (in most configurations)

`-S` = system account (no home directory, no password — just for running services).

```dockerfile
# Install curl for health checks
RUN apk add --no-cache curl
```

We need `curl` for the HEALTHCHECK command (see below).
`--no-cache` means don't store the package index (saves space).

Alpine uses `apk` instead of `apt-get` (Debian/Ubuntu).

```dockerfile
# Copy artifact from build stage
COPY --from=build /app/config-server/target/*.jar app.jar
```

This is the **multi-stage magic**. We reach back into the `build` stage
and copy ONLY the final JAR file. The build stage (Maven, source code,
all the intermediate .class files) is discarded.

The `*.jar` glob matches the built artifact (e.g., `config-server-1.0.0.jar`)
and renames it to `app.jar` for simplicity.

```dockerfile
# Set ownership
RUN chown payflow:payflow app.jar
USER payflow
```

Give the non-root user ownership of the JAR, then switch to that user.
Everything after `USER payflow` runs as the `payflow` user (including the ENTRYPOINT).

### 6.5 JVM Flags Explained

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"
```

| Flag | What it does | Why |
|------|-------------|-----|
| `-XX:+UseContainerSupport` | JVM respects container memory limits | Without this, JVM might think it has the HOST's full RAM |
| `-XX:MaxRAMPercentage=75.0` | Use at most 75% of container's memory for heap | Leaves 25% for JVM internals, native memory, stack, etc. |
| `-XX:+UseG1GC` | Use G1 garbage collector | Best for services with moderate heap (256MB–4GB), low pause times |
| `-XX:+HeapDumpOnOutOfMemoryError` | Write heap dump file on OOM | Helps diagnose memory leaks after the fact |
| `-Djava.security.egd=file:/dev/./urandom` | Use non-blocking random source | Without this, JVM startup can HANG waiting for entropy on Linux |

**Container Support deep dive:**
```
Without UseContainerSupport:
  Container limit: 512MB
  JVM sees host RAM: 16GB
  JVM allocates heap: 4GB (25% of 16GB)
  Container killed by OOM killer 💀

With UseContainerSupport:
  Container limit: 512MB
  JVM sees: 512MB
  JVM allocates heap: 384MB (75% of 512MB)
  Everything works ✅
```

### 6.6 HEALTHCHECK Directive

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8888/actuator/health || exit 1
```

| Parameter | Value | Meaning |
|-----------|-------|---------|
| `--interval` | 30s | Check health every 30 seconds |
| `--timeout` | 10s | If the check takes longer than 10s, consider it failed |
| `--start-period` | 30s | Ignore failures during the first 30s (JVM starting up) |
| `--retries` | 3 | Mark as unhealthy after 3 consecutive failures |

The command: `curl -f http://localhost:8888/actuator/health || exit 1`
- `curl -f` — fetch the URL, fail silently on HTTP errors (non-2xx)
- `/actuator/health` — Spring Boot Actuator's health endpoint (returns `{"status":"UP"}`)
- `|| exit 1` — if curl fails, return exit code 1 (unhealthy)

Docker uses this to determine if the container is healthy.
Other containers with `depends_on: condition: service_healthy` will wait
until this check passes before starting.

```dockerfile
EXPOSE 8888
```

Documents that the container listens on port 8888.
(This is informational — the actual port mapping happens in `docker-compose.yml`.)

```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

Why `sh -c` instead of just `java -jar app.jar`?
Because `$JAVA_OPTS` is an environment variable. Docker's exec form (`["java", ...]`)
doesn't expand environment variables. We need a shell (`sh -c`) to expand `$JAVA_OPTS`
into the individual JVM flags.

### 6.7 Differences from Service Registry's Dockerfile

The config-server Dockerfile is structurally identical to service-registry's.
The only differences are the module name references:

| Line | Service Registry | Config Server |
|------|-----------------|---------------|
| COPY POM | `COPY service-registry/pom.xml` | `COPY config-server/pom.xml` |
| Maven build | `-pl service-registry -am` | `-pl config-server -am` |
| COPY JAR | `COPY --from=build /app/service-registry/target/*.jar` | `COPY --from=build /app/config-server/target/*.jar` |
| EXPOSE | `EXPOSE 8761` | `EXPOSE 8888` |
| HEALTHCHECK | `curl ... localhost:8761/actuator/health` | `curl ... localhost:8888/actuator/health` |

Both need `common-lib` because both are child modules of the parent POM and
Maven needs to resolve the multi-module project structure. The pattern is
intentionally identical so that every PayFlow service follows the same
Dockerfile template — only the module name and port change.

---

## 7. How to Run & Verify

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

## 8. Startup Order (IMPORTANT)

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

## 9. What If Config Server Is Down?

```
Scenario: Config Server crashes after services already started.

Result: Running services CONTINUE working (they cached config at startup).
Only NEW services starting up will fail (can't fetch config).

Solution for production:
- Run 2+ instances of Config Server behind a load balancer
- Or use fallback: each service has default config in its own application.yml
```

---

## 10. Interview Notes

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
