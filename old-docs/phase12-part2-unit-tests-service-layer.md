# Phase 12 Part 2 — Unit Tests: Service Layer (JUnit 5 + Mockito)

## Goal
- Write isolated unit tests for PaymentService and MerchantService
- Mock repository and external dependencies with Mockito
- Achieve 80%+ line coverage on service classes

## Key Concept

```
┌─────────────────────────────────────────────────┐
│  Unit Test Isolation                            │
│                                                 │
│  Test Class                                     │
│       │                                         │
│       ▼                                         │
│  PaymentService (real)                          │
│       │                                         │
│       ├── PaymentRepository (mock)              │
│       ├── MerchantRepository (mock)             │
│       ├── KafkaTemplate (mock)                  │
│       └── BankConnector (mock)                  │
│                                                 │
│  Verify: interactions + return values           │
└─────────────────────────────────────────────────┘
```

## Prerequisites
- Spring Boot test dependencies in `pom.xml` (included by default)
- Service classes implemented

## Step-by-Step

### 1. Test Dependencies (already in `spring-boot-starter-test`)

```xml
<!-- pom.xml — these come with spring-boot-starter-test -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. PaymentService Unit Test

```java
package com.payflow.payment.service;

import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.entity.Payment;
import com.payflow.payment.entity.Merchant;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.MerchantRepository;
import com.payflow.common.constant.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private MerchantRepository merchantRepository;

    @InjectMocks private PaymentService paymentService;

    private Merchant testMerchant;
    private PaymentRequest validRequest;

    @BeforeEach
    void setUp() {
        testMerchant = new Merchant();
        testMerchant.setId(1L);
        testMerchant.setMerchantId("merchant_001");
        testMerchant.setActive(true);

        validRequest = new PaymentRequest();
        validRequest.setAmount(new BigDecimal("100.00"));
        validRequest.setCurrency("INR");
        validRequest.setMerchantId("merchant_001");
        validRequest.setPaymentMethod("CARD");
    }

    @Test
    void initiatePayment_success() {
        when(merchantRepository.findByMerchantId("merchant_001"))
            .thenReturn(Optional.of(testMerchant));
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.initiatePayment(validRequest);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void initiatePayment_merchantNotFound_throwsException() {
        when(merchantRepository.findByMerchantId("merchant_001"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Merchant not found");
    }

    @Test
    void initiatePayment_inactiveMerchant_throwsException() {
        testMerchant.setActive(false);
        when(merchantRepository.findByMerchantId("merchant_001"))
            .thenReturn(Optional.of(testMerchant));

        assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("inactive");
    }

    @Test
    void initiatePayment_negativeAmount_throwsException() {
        validRequest.setAmount(new BigDecimal("-50.00"));

        assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

### 3. MerchantService Unit Test

```java
package com.payflow.identity.service;

import com.payflow.identity.entity.Merchant;
import com.payflow.identity.repository.MerchantRepository;
import com.payflow.identity.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private MerchantService merchantService;

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest("test@example.com", "password123", "Test Store");
        when(merchantRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pw");
        when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Merchant result = merchantService.register(req);

        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(passwordEncoder).encode("password123");
        verify(merchantRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest req = new RegisterRequest("dup@example.com", "password123", "Store");
        when(merchantRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> merchantService.register(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already registered");
    }
}
```

### 4. Run Tests

```bash
cd payment-service
mvn test -pl . -Dtest="*ServiceTest"

# Run with coverage report
mvn test jacoco:report
# Report at: target/site/jacoco/index.html
```

## Verification

```bash
mvn test
# All tests should pass
# Expected output:
# Tests run: 6, Failures: 0, Errors: 0, Skipped: 0

# Check coverage
open target/site/jacoco/index.html
# Service classes should show 80%+ coverage
```

## Git Commit

```bash
git add payment-service/src/test identity-service/src/test
git commit -m "test: add unit tests for PaymentService and MerchantService"
```

## Next Step
→ **Phase 12 Part 3** — Integration tests with TestContainers
