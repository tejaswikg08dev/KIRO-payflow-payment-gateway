# Hands-On Guide — Phase 13 Part 1: Dockerfiles for Java Services

## Goal
- Multi-stage Dockerfile for each Java service
- Understanding of Docker layers, caching, image optimization
- Build and run a service as Docker container

---

## Multi-Stage Dockerfile (One for All Java Services)

```dockerfile
# ===== STAGE 1: Build =====
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
# Use Maven image to build (has JDK + Maven pre-installed)
# alpine = small base image (~100MB vs ~500MB)

WORKDIR /app
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY identity-service/pom.xml identity-service/
# Copy POMs first → downloads dependencies → cached if POMs don't change
RUN mvn dependency:go-offline -B
# Download ALL dependencies upfront (cached Docker layer)

COPY . .
RUN mvn clean package -DskipTests -pl common-lib,identity-service -am
# Build only the service we need (and its dependencies)
# -DskipTests: don't run tests during Docker build (tests run in CI)

# ===== STAGE 2: Runtime =====
FROM eclipse-temurin:17-jre-alpine
# Use JRE-only image (no compiler needed at runtime)
# Much smaller: ~180MB vs ~500MB with full JDK

WORKDIR /app
COPY --from=builder /app/identity-service/target/*.jar app.jar
# Copy ONLY the built JAR from stage 1
# All build tools, source code, dependencies are discarded (not in final image!)

EXPOSE 8081
# Document which port this service uses (doesn't actually publish it)

ENTRYPOINT ["java", "-jar", "app.jar"]
# Run the Spring Boot JAR
# JVM options can be added: -Xmx512m, -Dspring.profiles.active=prod
```

---

## Build and Run

```cmd
# Build Docker image:
docker build -f identity-service/Dockerfile -t payflow/identity-service:latest .

# Run container:
docker run -d -p 8081:8081 --name identity \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/payflow \
  payflow/identity-service:latest
```

---

## Why Multi-Stage?

```
SINGLE-STAGE (bad):
├── Final image has: JDK + Maven + source code + target JAR + all dependencies
├── Image size: ~800MB
└── Security risk: source code in production image!

MULTI-STAGE (good):
├── Stage 1 (builder): Has everything needed to BUILD
├── Stage 2 (runtime): Has ONLY JRE + compiled JAR
├── Image size: ~200MB
└── Secure: No source code, no build tools in production
```

---

## Next Step → Phase 13 Parts 2-4 (React Dockerfiles, docker-compose, networking)
