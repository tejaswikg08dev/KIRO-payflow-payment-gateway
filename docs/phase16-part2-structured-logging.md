# Phase 16 Part 2 — Structured Logging & Correlation IDs

## Goal
- Configure JSON-structured logging for all Java services
- Implement correlation ID propagation across service boundaries
- Enable log aggregation readiness (CloudWatch, ELK compatible)

## Key Concept

```
┌────────────────────────────────────────────────────────────────┐
│  Correlation ID Flow                                           │
│                                                                │
│  Client Request                                                │
│  X-Correlation-Id: abc-123                                     │
│       │                                                        │
│       ▼                                                        │
│  API Gateway (logs with correlationId=abc-123)                 │
│       │ Header forwarded                                       │
│       ▼                                                        │
│  Payment Service (logs with correlationId=abc-123)             │
│       │ Kafka message header                                   │
│       ▼                                                        │
│  Notification Service (logs with correlationId=abc-123)        │
│                                                                │
│  All logs for one request share the same correlation ID!       │
└────────────────────────────────────────────────────────────────┘
```

## Prerequisites
- Spring Boot services running
- Logback (default Spring Boot logger)

## Step-by-Step

### 1. Add Logstash Encoder Dependency

```xml
<!-- pom.xml (each service) -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### 2. Logback Configuration (`src/main/resources/logback-spring.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="SERVICE_NAME" source="spring.application.name"/>

    <!-- Console: JSON format for production -->
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${SERVICE_NAME}"}</customFields>
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <thread>thread</thread>
                <logger>logger</logger>
                <level>level</level>
                <message>message</message>
                <stackTrace>stackTrace</stackTrace>
            </fieldNames>
            <includeMdcKeyName>correlationId</includeMdcKeyName>
            <includeMdcKeyName>merchantId</includeMdcKeyName>
            <includeMdcKeyName>transactionId</includeMdcKeyName>
        </encoder>
    </appender>

    <!-- Human-readable for local development -->
    <appender name="DEV_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{correlationId:-}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <springProfile name="prod,staging">
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="default,dev">
        <root level="DEBUG">
            <appender-ref ref="DEV_CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

### 3. Correlation ID Filter (`common-lib`)

```java
package com.payflow.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC = "correlationId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CORRELATION_ID_MDC, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC);
        }
    }
}
```

### 4. Propagate Correlation ID in RestTemplate/WebClient

```java
package com.payflow.common.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                request.getHeaders().add("X-Correlation-Id", correlationId);
            }
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
```

### 5. Kafka Header Propagation

```java
package com.payflow.common.kafka;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CorrelationIdProducerInterceptor implements ProducerInterceptor<String, String> {

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override public void onAcknowledgement(org.apache.kafka.clients.producer.RecordMetadata m, Exception e) {}
    @Override public void close() {}
    @Override public void configure(Map<String, ?> configs) {}
}
```

### 6. Structured Log Output Example

```json
{
  "timestamp": "2024-01-15T10:23:45.123Z",
  "level": "INFO",
  "service": "payment-service",
  "correlationId": "abc-123-def-456",
  "merchantId": "mch_001",
  "transactionId": "txn_789",
  "logger": "com.payflow.payment.service.PaymentService",
  "message": "Payment initiated",
  "thread": "http-nio-8082-exec-1"
}
```

## Verification

```bash
# Start service with prod profile
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run

# Make a request
curl -H "X-Correlation-Id: test-corr-123" http://localhost:8080/api/v1/payments

# Check logs — should be JSON with correlationId
# {"timestamp":"...","correlationId":"test-corr-123","level":"INFO",...}

# Verify response header
curl -v http://localhost:8080/api/v1/health 2>&1 | grep "X-Correlation-Id"
# X-Correlation-Id: <generated-uuid>
```

## Git Commit

```bash
git add common-lib/src/main/java/com/payflow/common/filter
git add */src/main/resources/logback-spring.xml
git commit -m "feat(logging): add structured JSON logging with correlation IDs"
```

## Next Step
→ **Phase 16 Part 3** — CloudWatch dashboards and alarms
