# PayFlow — Low-Level Design (LLD) Document

**Document Version:** 2.0  
**Last Updated:** August 2026  
**Purpose:** Detailed technical design for implementation

---

## 1. Common Library Design

### 1.1 Package Structure

```
common-lib/
└── src/main/java/com/payflow/common/
    ├── dto/
    │   ├── ApiResponse.java        # Standard response wrapper
    │   ├── ErrorDetail.java        # Error structure
    │   └── PagedResponse.java      # Pagination wrapper
    ├── constant/
    │   ├── PaymentStatus.java      # CREATED, AUTHORIZED, CAPTURED...
    │   └── PaymentMethod.java      # CARD, UPI, NETBANKING
    ├── exception/
    │   ├── PayflowException.java   # Base exception
    │   ├── ResourceNotFoundException.java
    │   ├── DuplicateResourceException.java
    │   └── GlobalExceptionHandler.java
    └── util/
        └── IdGenerator.java        # Generate pay_xxx, ord_xxx
```

### 1.2 ID Generation Pattern

```java
public class IdGenerator {
    public static String generateOrderId() {
        return "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    public static String generatePaymentId() {
        return "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    public static String generateRefundId() {
        return "ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

---

## 2. Payment Service Design

### 2.1 State Machine Implementation

```java
public enum PaymentStatus {
    CREATED,
    PROCESSING,
    AUTHORIZED,
    CAPTURED,
    VOIDED,
    REFUNDED,
    FAILED,
    SETTLED
}

public class PaymentStateMachine {
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
        CREATED, Set.of(PROCESSING, FAILED),
        PROCESSING, Set.of(AUTHORIZED, FAILED),
        AUTHORIZED, Set.of(CAPTURED, VOIDED, FAILED),
        CAPTURED, Set.of(REFUNDED, SETTLED),
        SETTLED, Set.of(REFUNDED)
    );
    
    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
```


### 2.2 Idempotency Implementation

```java
@Service
public class IdempotencyService {
    private final StringRedisTemplate redis;
    private static final Duration TTL = Duration.ofHours(24);
    
    public boolean isProcessed(String key) {
        return redis.hasKey("idempotency:" + key);
    }
    
    public void markProcessed(String key, String paymentId) {
        redis.opsForValue().set(
            "idempotency:" + key, 
            paymentId, 
            TTL
        );
    }
    
    public Optional<String> getExistingPayment(String key) {
        return Optional.ofNullable(redis.opsForValue().get("idempotency:" + key));
    }
}
```

### 2.3 Payment Processing Flow

```java
@Service
public class PaymentProcessorService {
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // 1. Check idempotency
        if (idempotencyService.isProcessed(request.getIdempotencyKey())) {
            return getExistingPayment(request.getIdempotencyKey());
        }
        
        // 2. Validate order
        Order order = orderService.getOrder(request.getOrderId());
        validateOrder(order);
        
        // 3. Create payment record
        Payment payment = createPayment(order, request);
        
        // 4. Fraud check
        int fraudScore = fraudService.calculateScore(payment);
        payment.setFraudScore(fraudScore);
        
        if (fraudScore > 90) {
            return declinePayment(payment, "High fraud risk");
        }
        
        // 5. Route to bank
        RouteResponse bankResponse = routingService.route(payment);
        
        // 6. Update payment status
        updatePaymentStatus(payment, bankResponse);
        
        // 7. Mark idempotency
        idempotencyService.markProcessed(
            request.getIdempotencyKey(), 
            payment.getId()
        );
        
        // 8. Publish event
        eventPublisher.publish(new PaymentEvent(payment));
        
        return PaymentResponse.from(payment);
    }
}
```

---

## 3. ISO 8583 Implementation

### 3.1 Message Structure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ISO 8583 Message Format                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   [MTI][Bitmap][Data Elements]                                              │
│                                                                              │
│   MTI (4 bytes): 0100 = Authorization Request                               │
│                  0110 = Authorization Response                              │
│                  0400 = Reversal Request                                    │
│                                                                              │
│   Bitmap (8 or 16 bytes): Indicates which fields are present                │
│   Example: 0x7234054128C28001 means fields 2,3,4,7,11,12,14,18,22,23,25... │
│                                                                              │
│   Key Fields:                                                                │
│   Field 2: Primary Account Number (PAN)                                     │
│   Field 3: Processing Code                                                  │
│   Field 4: Transaction Amount                                               │
│   Field 11: System Trace Audit Number                                       │
│   Field 38: Authorization Code                                              │
│   Field 39: Response Code (00=Approved, 51=Insufficient funds)              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```


### 3.2 Encoder/Decoder

```java
public class Iso8583Encoder {
    public byte[] encode(Iso8583Message message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Write MTI
        baos.write(message.getMti().getBytes());
        
        // Calculate and write bitmap
        byte[] bitmap = calculateBitmap(message.getFields());
        baos.write(bitmap);
        
        // Write data elements
        for (int fieldNum : message.getFields().keySet()) {
            FieldDefinition def = FieldDefinitions.get(fieldNum);
            String value = message.getField(fieldNum);
            byte[] encoded = encodeField(def, value);
            baos.write(encoded);
        }
        
        return baos.toByteArray();
    }
}
```

---

## 4. Fraud Detection Design

### 4.1 Rule Engine

```java
public class FraudRuleEngine {
    private final List<FraudRule> rules = List.of(
        new VelocityRule(),      // Max 5 transactions per minute
        new AmountRule(),        // Flag amounts > 50000
        new GeographyRule(),     // Different country from usual
        new TimeRule(),          // Unusual hours (2 AM - 5 AM)
        new BinRiskRule()        // High-risk card BINs
    );
    
    public int calculateScore(Payment payment) {
        int totalScore = 0;
        for (FraudRule rule : rules) {
            totalScore += rule.evaluate(payment);
        }
        return Math.min(totalScore, 100);
    }
}

public interface FraudRule {
    int evaluate(Payment payment);  // Returns 0-30 points
}
```

---

## 5. Webhook Service Design

### 5.1 HMAC Signature

```java
public class SignatureGenerator {
    public String generate(String payload, String secret) {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + bytesToHex(hash);
    }
}
```

### 5.2 Retry Strategy

```java
public class ExponentialBackoff {
    private static final int[] DELAYS = {0, 60, 300, 900, 3600}; // seconds
    
    public int getDelayForAttempt(int attempt) {
        if (attempt >= DELAYS.length) {
            return -1; // No more retries
        }
        return DELAYS[attempt];
    }
}
```

---

## 6. Settlement Batch Design

### 6.1 Spring Batch Job

```java
@Configuration
public class SettlementJobConfig {
    
    @Bean
    public Job settlementJob() {
        return jobBuilder.get("settlementJob")
            .start(fetchPaymentsStep())
            .next(calculateFeesStep())
            .next(createSettlementStep())
            .next(notifyMerchantsStep())
            .build();
    }
    
    @Bean
    public Step fetchPaymentsStep() {
        return stepBuilder.get("fetchPayments")
            .<Payment, SettlementItem>chunk(100)
            .reader(paymentReader())
            .processor(settlementProcessor())
            .writer(settlementWriter())
            .build();
    }
}
```

### 6.2 Fee Calculation

```java
public class FeeCalculator {
    public FeeBreakdown calculate(BigDecimal amount, MerchantFeeConfig config) {
        // MDR (Merchant Discount Rate)
        BigDecimal mdr = amount.multiply(config.getMdrPercent())
            .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        
        // Fixed fee
        BigDecimal fixedFee = config.getFixedFee();
        
        // GST on fees (18%)
        BigDecimal totalFee = mdr.add(fixedFee);
        BigDecimal gst = totalFee.multiply(GST_RATE);
        
        return new FeeBreakdown(mdr, fixedFee, gst, totalFee.add(gst));
    }
}
```

---

## 7. Circuit Breaker Configuration

```java
@Configuration
public class ResilienceConfig {
    
    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();
    }
    
    @Bean
    public RetryConfig retryConfig() {
        return RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(IOException.class, TimeoutException.class)
            .build();
    }
}
```

---

## Related Documents

- [Requirements](./requirements-complete.md)
- [HLD](./hld-complete.md)
- [Database](./database-complete.md)
- [API](./api-complete.md)

---

**End of LLD Document**
