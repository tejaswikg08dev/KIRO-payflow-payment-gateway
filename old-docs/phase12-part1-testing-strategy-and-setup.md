# Hands-On Guide — Phase 12 Part 1: Testing Strategy & Setup

## Goal
- Understanding of test pyramid (unit → integration → E2E)
- Test frameworks: JUnit 5, Mockito, TestContainers, REST Assured
- Maven test configuration
- First unit test running

---

## Test Pyramid

```
                    /\
                   /  \
                  / E2E \        ← Few (slow, expensive, full stack)
                 /________\
                /          \
               / Integration \   ← Some (real DB via TestContainers)
              /______________\
             /                \
            /    Unit Tests     \  ← Many (fast, isolated, mock everything)
           /____________________\

OUR TESTING STRATEGY:
├── Unit Tests (Phase 12 Part 2): Test service layer with Mockito
│   ├── AuthServiceTest: register + login logic
│   ├── PaymentProcessorServiceTest: authorize + capture + void
│   ├── FeeCalculatorTest: MDR + GST math
│   └── IdempotencyServiceTest: Redis cache behavior
│
├── Integration Tests (Phase 12 Part 3): Real DB with TestContainers
│   ├── PaymentRepositoryTest: actual PostgreSQL queries
│   ├── PaymentFlowIntegrationTest: create order → pay → capture
│   └── ISO8583EncoderDecoderTest: encode → decode roundtrip
│
└── API Tests (Phase 12 Part 4): REST Assured against running app
    ├── Full HTTP request → response validation
    ├── Error response format verification
    └── Authentication/authorization testing
```

---

## Dependencies (in parent pom.xml, inherited by all)

```xml
<!-- Already present: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <!-- Includes: JUnit 5, Mockito, AssertJ, Spring Test -->
</dependency>

<!-- Add for integration tests: -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.4.0</version>
    <scope>test</scope>
</dependency>
```

---

## Run Tests

```cmd
# Run all tests for one module:
cd payment-service
mvn test

# Run specific test class:
mvn test -Dtest=PaymentProcessorServiceTest

# Run all tests across ALL modules:
cd payflow-payment-gateway
mvn test
```

---

## Next Step → Phase 12 Parts 2-4 (unit tests, integration tests, coverage)
