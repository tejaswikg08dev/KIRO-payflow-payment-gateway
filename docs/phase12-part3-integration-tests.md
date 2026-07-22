# Phase 12 Part 3 — Integration Tests with TestContainers

## Goal
- Write integration tests using real PostgreSQL via TestContainers
- Test full request→repository→database round trips
- Validate JPA mappings and Flyway migrations against real DB

## Key Concept

```
┌────────────────────────────────────────────────────┐
│  Integration Test Lifecycle                        │
│                                                    │
│  @BeforeAll                                        │
│       │                                            │
│       ▼                                            │
│  TestContainers starts PostgreSQL (Docker)         │
│       │                                            │
│       ▼                                            │
│  Spring Boot loads with dynamic datasource URL     │
│       │                                            │
│       ▼                                            │
│  Flyway runs migrations (V1__init.sql, etc.)       │
│       │                                            │
│       ▼                                            │
│  Tests execute against real database               │
│       │                                            │
│       ▼                                            │
│  @AfterAll: Container destroyed                    │
└────────────────────────────────────────────────────┘
```

## Prerequisites
- Docker running on development machine
- TestContainers dependency added to pom.xml

### Add Dependencies

```xml
<!-- pom.xml test dependencies -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

## Step-by-Step

### 1. Base Integration Test Class

```java
package com.payflow.payment;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("payflow_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
```

### 2. Payment Repository Integration Test

```java
package com.payflow.payment.repository;

import com.payflow.payment.BaseIntegrationTest;
import com.payflow.payment.entity.Payment;
import com.payflow.common.constant.PaymentStatus;
import com.payflow.common.constant.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class PaymentRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void saveAndRetrievePayment() {
        Payment payment = Payment.builder()
            .transactionId("txn_test_001")
            .merchantId("merchant_001")
            .amount(new BigDecimal("250.00"))
            .currency("INR")
            .status(PaymentStatus.PENDING)
            .paymentMethod(PaymentMethod.CARD)
            .createdAt(LocalDateTime.now())
            .build();

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTransactionId()).isEqualTo("txn_test_001");
    }

    @Test
    void findByMerchantIdWithPagination() {
        // Insert 5 payments for same merchant
        for (int i = 0; i < 5; i++) {
            paymentRepository.save(Payment.builder()
                .transactionId("txn_page_" + i)
                .merchantId("merchant_page")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.UPI)
                .createdAt(LocalDateTime.now())
                .build());
        }

        Page<Payment> page = paymentRepository.findByMerchantId("merchant_page", PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findByStatusFilter() {
        paymentRepository.save(Payment.builder()
            .transactionId("txn_status_1").merchantId("m1")
            .amount(new BigDecimal("50.00")).currency("INR")
            .status(PaymentStatus.FAILED).paymentMethod(PaymentMethod.CARD)
            .createdAt(LocalDateTime.now()).build());

        var results = paymentRepository.findByStatus(PaymentStatus.FAILED);

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(p -> p.getStatus() == PaymentStatus.FAILED);
    }
}
```

### 3. Flyway Migration Test

```java
package com.payflow.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.*;

class FlywayMigrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void allMigrationsApplySuccessfully() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, "payments", null);
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void flyway_history_table_exists() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, "flyway_schema_history", null);
            assertThat(rs.next()).isTrue();
        }
    }
}
```

### 4. Test Application Properties (`src/test/resources/application-test.yml`)

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles DDL
    show-sql: true
  kafka:
    bootstrap-servers: localhost:9092  # Override or disable in tests
logging:
  level:
    org.testcontainers: INFO
    com.payflow: DEBUG
```

## Verification

```bash
# Ensure Docker is running
docker info

# Run integration tests
cd payment-service
mvn test -Dtest="*IntegrationTest" -Dsurefire.useFile=false

# Expected output:
# PostgreSQL container starting...
# Flyway migrations applied
# Tests run: 4, Failures: 0, Errors: 0
```

## Git Commit

```bash
git add payment-service/src/test
git commit -m "test: add integration tests with TestContainers PostgreSQL"
```

## Next Step
→ **Phase 12 Part 4** — API tests with REST Assured and JaCoCo coverage
