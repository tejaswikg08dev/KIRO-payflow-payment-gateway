# Phase 12 Part 4 — API Tests (REST Assured) & JaCoCo Coverage

## Goal
- Write end-to-end API tests using REST Assured
- Configure JaCoCo for code coverage reporting
- Set minimum coverage threshold (80%) to fail the build

## Key Concept

```
┌────────────────────────────────────────────────────┐
│  API Test Stack                                    │
│                                                    │
│  REST Assured                                      │
│       │  HTTP Request                              │
│       ▼                                            │
│  Embedded Spring Boot (random port)                │
│       │                                            │
│       ▼                                            │
│  Controller → Service → Repository                 │
│       │                                            │
│       ▼                                            │
│  TestContainers PostgreSQL                         │
│                                                    │
│  JaCoCo agent instruments all classes              │
│  → Generates coverage report after tests           │
└────────────────────────────────────────────────────┘
```

## Prerequisites
- Phase 12 Part 3 completed
- Docker running for TestContainers

### Add Dependencies

```xml
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
```

### Add JaCoCo Plugin

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Step-by-Step

### 1. Base API Test Class

```java
package com.payflow.payment.api;

import com.payflow.payment.BaseIntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;

public abstract class BaseApiTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";
    }
}
```

### 2. Payment API Test

```java
package com.payflow.payment.api;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

class PaymentApiTest extends BaseApiTest {

    @Test
    void createPayment_returnsCreated() {
        given()
            .contentType(ContentType.JSON)
            .header("X-API-Key", "pk_tst_valid_key")
            .body("""
                {
                    "amount": 500.00,
                    "currency": "INR",
                    "paymentMethod": "CARD",
                    "description": "Test payment",
                    "cardNumber": "4242424242424242",
                    "expiryMonth": "12",
                    "expiryYear": "2025",
                    "cvv": "123"
                }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .body("data.transactionId", notNullValue())
            .body("data.status", equalTo("PENDING"))
            .body("data.amount", equalTo(500.00f));
    }

    @Test
    void createPayment_invalidAmount_returns400() {
        given()
            .contentType(ContentType.JSON)
            .header("X-API-Key", "pk_tst_valid_key")
            .body("""
                { "amount": -100, "currency": "INR", "paymentMethod": "CARD" }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(400)
            .body("error", containsString("amount"));
    }

    @Test
    void createPayment_noApiKey_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                { "amount": 100, "currency": "INR", "paymentMethod": "CARD" }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(401);
    }

    @Test
    void getPayment_exists_returns200() {
        // First create a payment
        String txnId = given()
            .contentType(ContentType.JSON)
            .header("X-API-Key", "pk_tst_valid_key")
            .body("""
                { "amount": 200, "currency": "INR", "paymentMethod": "UPI", "vpa": "test@upi" }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(201)
            .extract().path("data.transactionId");

        // Then retrieve it
        given()
            .header("X-API-Key", "pk_tst_valid_key")
        .when()
            .get("/payments/" + txnId)
        .then()
            .statusCode(200)
            .body("data.transactionId", equalTo(txnId));
    }

    @Test
    void getPayment_notFound_returns404() {
        given()
            .header("X-API-Key", "pk_tst_valid_key")
        .when()
            .get("/payments/txn_nonexistent")
        .then()
            .statusCode(404);
    }
}
```

### 3. Health Check API Test

```java
package com.payflow.payment.api;

import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

class HealthApiTest extends BaseApiTest {

    @Test
    void healthEndpoint_returns200() {
        given()
        .when()
            .get("/actuator/health")
        .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
}
```

### 4. Run Tests with Coverage

```bash
# Run all tests with JaCoCo
mvn clean verify

# View coverage report
open target/site/jacoco/index.html
```

## Verification

```bash
mvn clean verify
# Expected:
# [INFO] All coverage checks passed.
# Tests run: 8, Failures: 0, Errors: 0

# Check coverage report
ls target/site/jacoco/index.html
# Open in browser — verify 80%+ line coverage

# If coverage fails:
# [ERROR] Coverage checks have not been met: LINE 0.72 < 0.80
# → Add more tests to uncovered paths
```

## Git Commit

```bash
git add payment-service/src/test payment-service/pom.xml
git commit -m "test: add REST Assured API tests and JaCoCo 80% coverage gate"
```

## Next Step
→ **Phase 13 Part 2** — Dockerfiles for React frontends
