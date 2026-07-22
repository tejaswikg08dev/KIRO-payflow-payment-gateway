# ============================================
# PayFlow Webhook Service - Multi-stage Build
# ============================================

# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy parent POM and common-lib for dependency resolution
COPY pom.xml ./pom.xml
COPY common-lib/pom.xml ./common-lib/pom.xml
COPY common-lib/src ./common-lib/src
COPY webhook-service/pom.xml ./webhook-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl webhook-service -am -B

# Copy source code
COPY webhook-service/src ./webhook-service/src

# Build the application
RUN mvn clean package -DskipTests -pl webhook-service -am

# Stage 2: Runtime with JRE only
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S payflow && adduser -S payflow -G payflow

# Install curl for health checks
RUN apk add --no-cache curl

# Copy artifact from build stage
COPY --from=build /app/webhook-service/target/*.jar app.jar

# Set ownership
RUN chown payflow:payflow app.jar
USER payflow

# JVM configuration for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
