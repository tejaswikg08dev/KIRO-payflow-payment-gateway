# Phase 2 — Part 2: Low-Level Design (LLD)

> Class diagrams, interfaces, and design patterns for each service.
> This is what you'd explain when asked "How did you implement this?"

---

## 1. Common Design Patterns Across All Services

### 1.1 Standard Package Structure (Every Service)

```
com.payflow.{servicename}/
├── {ServiceName}Application.java          ← @SpringBootApplication
├── controller/                            ← REST endpoints (@RestController)
│   └── XxxController.java
├── service/                               ← Business logic (@Service)
│   ├── XxxService.java                    ← Interface
│   └── XxxServiceImpl.java               ← Implementation
├── repository/                            ← Data access (@Repository)
│   └── XxxRepository.java                ← extends JpaRepository
├── model/                                 ← JPA entities (@Entity)
│   └── Xxx.java
├── dto/                                   ← Request/Response objects
│   ├── XxxRequest.java
│   └── XxxResponse.java
├── mapper/                                ← Entity ↔ DTO conversion
│   └── XxxMapper.java                    ← @Mapper (MapStruct)
├── config/                                ← Configuration classes
│   └── XxxConfig.java
├── exception/                             ← Custom exceptions + handler
│   ├── XxxException.java
│   └── GlobalExceptionHandler.java
└── client/                                ← Feign clients (calls to other services)
    └── XxxClient.java                     ← @FeignClient
```

### 1.2 Standard REST Controller Pattern

```java
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment lifecycle management")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Create and authorize a payment")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment authorized"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "422", description = "Payment declined")
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Merchant-Id") String merchantId) {
        
        PaymentResponse response = paymentService.processPayment(request, idempotencyKey, merchantId);
        return ResponseEntity.status(201).body(ApiResponse.success(response));
    }
}
```

### 1.3 Standard Service Pattern

```java
public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest request, String idempotencyKey, String merchantId);
    PaymentResponse capturePayment(String paymentId, CaptureRequest request);
    PaymentResponse voidPayment(String paymentId);
    RefundResponse refundPayment(String paymentId, RefundRequest request);
}

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final RoutingServiceClient routingClient;
    private final FraudService fraudService;
    private final PaymentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    
    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey, String merchantId) {
        // 1. Check idempotency
        // 2. Fraud check
        // 3. Route to bank
        // 4. Save result
        // 5. Publish event
        // 6. Return response
    }
}
```

---

## 2. Payment Service — Detailed Class Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                         PAYMENT SERVICE                                  │
│                                                                          │
│  CONTROLLERS:                                                            │
│  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────┐ │
│  │  OrderController   │  │ PaymentController  │  │ RefundController │ │
│  ├────────────────────┤  ├────────────────────┤  ├──────────────────┤ │
│  │ POST /v1/orders    │  │ POST /v1/payments  │  │ POST /refunds    │ │
│  │ GET  /v1/orders/{id│  │ POST /{id}/capture │  │ GET  /refunds/{id│ │
│  │ GET  /v1/orders    │  │ POST /{id}/void    │  │ GET  /refunds    │ │
│  └────────┬───────────┘  │ GET  /{id}         │  └────────┬─────────┘ │
│           │               │ GET  /             │           │           │
│           │               └────────┬───────────┘           │           │
│           │                        │                        │           │
│           ▼                        ▼                        ▼           │
│  SERVICES:                                                              │
│  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────┐ │
│  │   OrderService     │  │  PaymentProcessor  │  │  RefundService   │ │
│  ├────────────────────┤  ├────────────────────┤  ├──────────────────┤ │
│  │ createOrder()      │  │ processPayment()   │  │ createRefund()   │ │
│  │ getOrder()         │  │ capturePayment()   │  │ processRefund()  │ │
│  │ expireOrder()      │  │ voidPayment()      │  │ getRefund()      │ │
│  └────────────────────┘  └────────┬───────────┘  └──────────────────┘ │
│                                    │                                     │
│                                    │ uses                                │
│           ┌────────────────────────┼────────────────────────┐           │
│           ▼                        ▼                        ▼           │
│  ┌────────────────────┐  ┌────────────────────┐  ┌──────────────────┐ │
│  │IdempotencyService  │  │PaymentStateMachine │  │  FraudService    │ │
│  ├────────────────────┤  ├────────────────────┤  ├──────────────────┤ │
│  │ check(key)         │  │ transition(event)  │  │ evaluate(txn)    │ │
│  │ store(key, resp)   │  │ getCurrentState()  │  │ → riskScore      │ │
│  │ (Redis-backed)     │  │ isValidTransition()│  │ → decision       │ │
│  └────────────────────┘  └────────────────────┘  └──────────────────┘ │
│                                                                          │
│  FEIGN CLIENTS (calls to other services):                               │
│  ┌────────────────────┐  ┌────────────────────┐                        │
│  │RoutingServiceClient│  │MerchantServiceClient│                       │
│  ├────────────────────┤  ├────────────────────┤                        │
│  │ routePayment()     │  │ getMerchant()      │                        │
│  │ (→ Routing:8084)   │  │ (→ Merchant:8082)  │                        │
│  └────────────────────┘  └────────────────────┘                        │
│                                                                          │
│  MODELS (JPA Entities):                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐│
│  │    Order     │  │   Payment    │  │    Refund    │  │StateHistory││
│  ├──────────────┤  ├──────────────┤  ├──────────────┤  ├────────────┤│
│  │ id           │  │ id           │  │ id           │  │ id         ││
│  │ merchantId   │  │ orderId      │  │ paymentId    │  │ paymentId  ││
│  │ amount       │  │ amount       │  │ amount       │  │ fromState  ││
│  │ status       │  │ status       │  │ status       │  │ toState    ││
│  │ receipt      │  │ method       │  │ reason       │  │ event      ││
│  │ expiresAt    │  │ authCode     │  │ rrn          │  │ createdAt  ││
│  └──────────────┘  │ riskScore    │  └──────────────┘  └────────────┘│
│                     │ routeId      │                                    │
│                     └──────────────┘                                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Routing Service — Class Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ROUTING SERVICE                                   │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    RoutingController                              │   │
│  │  POST /internal/route  → routePayment(RouteRequest)              │   │
│  │  GET  /routes/metrics  → getRouteMetrics()                       │   │
│  └──────────────────────────────┬──────────────────────────────────┘   │
│                                  │                                       │
│                                  ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    RoutingEngine                                   │   │
│  │                                                                   │   │
│  │  routePayment(request):                                          │   │
│  │    1. Get all available routes                                    │   │
│  │    2. Score each route (AI scoring)                               │   │
│  │    3. Pick best route                                             │   │
│  │    4. Build ISO 8583 message                                      │   │
│  │    5. Send to bank via TCP                                        │   │
│  │    6. Parse response                                              │   │
│  │    7. If failed → failover to next route                          │   │
│  │    8. Update route metrics                                        │   │
│  │    9. Return result                                               │   │
│  └──────────────────────────────┬──────────────────────────────────┘   │
│                                  │                                       │
│           ┌──────────────────────┼──────────────────────┐               │
│           ▼                      ▼                      ▼               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
│  │  «interface»    │  │  «interface»    │  │  «interface»    │        │
│  │ RoutingStrategy │  │ Iso8583Codec    │  │ BankClient      │        │
│  ├─────────────────┤  ├─────────────────┤  ├─────────────────┤        │
│  │ score(route,txn)│  │ encode(msg)     │  │ send(bytes)     │        │
│  │ → double        │  │ decode(bytes)   │  │ → bytes         │        │
│  └────────┬────────┘  └─────────────────┘  └────────┬────────┘        │
│           │                                           │                 │
│    ┌──────┼──────────────┐                    ┌──────┴───────┐         │
│    ▼      ▼              ▼                    ▼              │         │
│  ┌─────┐┌──────────┐┌──────────┐      ┌──────────────┐     │         │
│  │Cost ││SuccessRate││  AI      │      │  NettyTcp    │     │         │
│  │Based││Based      ││Bandit    │      │  Client      │     │         │
│  │Router│Router     ││Router    │      │              │     │         │
│  └─────┘└──────────┘└──────────┘      │ connect()    │     │         │
│                                         │ sendAndWait()│     │         │
│                                         │ close()      │     │         │
│                                         └──────────────┘     │         │
│                                                               │         │
│  ISO 8583 Package:                                           │         │
│  ┌──────────────────────────────────────────────────────┐    │         │
│  │ iso8583/                                              │    │         │
│  │ ├── Iso8583Message.java   (MTI + bitmap + fields)    │    │         │
│  │ ├── MessageType.java      (enum: AUTH_REQUEST, etc)  │    │         │
│  │ ├── FieldDefinition.java  (type, length per field)   │    │         │
│  │ ├── BitmapUtil.java       (set/get bits)             │    │         │
│  │ ├── Iso8583Encoder.java   (Java → bytes)             │    │         │
│  │ └── Iso8583Decoder.java   (bytes → Java)             │    │         │
│  └──────────────────────────────────────────────────────┘    │         │
│                                                               │         │
└───────────────────────────────────────────────────────────────┘         │
```

---

## 4. Settlement Service — Class Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                       SETTLEMENT SERVICE                                 │
│                                                                          │
│  ┌─────────────────────┐                                                │
│  │ SettlementScheduler │  ← @Scheduled(cron = "0 0 0 * * ?") midnight  │
│  │   triggerBatch()    │                                                │
│  └──────────┬──────────┘                                                │
│             │ launches                                                   │
│             ▼                                                            │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              Spring Batch Job: "settlementJob"                    │   │
│  │                                                                   │   │
│  │  Step 1: Read         Step 2: Process       Step 3: Write        │   │
│  │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │   │
│  │  │Settlement    │    │Settlement    │    │Settlement    │      │   │
│  │  │Reader        │───►│Processor     │───►│Writer        │      │   │
│  │  │              │    │              │    │              │      │   │
│  │  │Fetch CAPTURED│    │Group by merch│    │Save settlement│     │   │
│  │  │payments from │    │Calculate fees│    │records to DB  │     │   │
│  │  │PaymentService│    │Compute net   │    │Initiate payout│     │   │
│  │  │(Feign call)  │    │              │    │Publish event  │     │   │
│  │  └──────────────┘    └──────────────┘    └──────────────┘      │   │
│  │                                                                   │   │
│  │  Chunk size: 100 payments per batch                              │   │
│  │  Skip policy: Skip failed records, continue processing           │   │
│  │  Restart: Can restart from last checkpoint on failure             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌──────────────────┐  ┌──────────────────┐                            │
│  │  FeeCalculator   │  │  PayoutService   │                            │
│  ├──────────────────┤  ├──────────────────┤                            │
│  │ calculate(       │  │ initiatePayout() │                            │
│  │   amount,        │  │ getPayoutStatus()│                            │
│  │   method,        │  │ retryPayout()    │                            │
│  │   merchantFee)   │  └──────────────────┘                            │
│  │ → {fee, gst, net}│                                                   │
│  └──────────────────┘                                                    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Webhook Service — Class Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        WEBHOOK SERVICE                                    │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │             SqsEventListener (@SqsListener)                       │   │
│  │                                                                   │   │
│  │  Listens to: payment-events-queue                                │   │
│  │  Receives: {event_type, payment_id, merchant_id, data}           │   │
│  │  Action: Pass to WebhookDispatcher                               │   │
│  └──────────────────────────────┬──────────────────────────────────┘   │
│                                  │                                       │
│                                  ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │             WebhookDispatcher                                     │   │
│  │                                                                   │   │
│  │  1. Look up merchant's webhook URL (from merchant-service)        │   │
│  │  2. Build webhook payload (JSON)                                  │   │
│  │  3. Sign with HMAC-SHA256 (using merchant's webhook secret)       │   │
│  │  4. POST to merchant's URL                                        │   │
│  │  5. If 2xx → mark delivered (DynamoDB)                            │   │
│  │  6. If fail → schedule retry (RetryScheduler)                     │   │
│  └──────────────────────────────┬──────────────────────────────────┘   │
│                                  │                                       │
│          ┌───────────────────────┼───────────────────────┐              │
│          ▼                       ▼                       ▼              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐        │
│  │SignatureGenerator│  │ RetryScheduler  │  │DeadLetterHandler│        │
│  ├─────────────────┤  ├─────────────────┤  ├─────────────────┤        │
│  │ sign(payload,   │  │ schedule(event, │  │ handleFailed()  │        │
│  │      secret)    │  │   attemptNum)   │  │ (after 5 fails) │        │
│  │ → hmac-sha256   │  │                 │  │ → save to DLQ   │        │
│  │                 │  │ Delays:          │  │ → alert ops     │        │
│  │ verify(payload, │  │ 1: 5min         │  │                 │        │
│  │   signature,    │  │ 2: 30min        │  │                 │        │
│  │   secret)       │  │ 3: 2hrs         │  │                 │        │
│  │ → boolean       │  │ 4: 24hrs        │  │                 │        │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘        │
│                                                                          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Key Interfaces (Contracts Between Components)

```java
// === ROUTING ===
public interface RoutingStrategy {
    double scoreRoute(Route route, PaymentContext context);
    String getStrategyName();
}

// === PAYMENT HANDLER ===
public interface PaymentMethodHandler {
    PaymentResult authorize(PaymentRequest request);
    PaymentResult capture(String paymentId, BigDecimal amount);
    PaymentResult refund(String paymentId, BigDecimal amount);
    boolean supports(PaymentMethod method);
}

// Implementations:
// - CardPaymentHandler (builds ISO 8583, calls routing)
// - UpiPaymentHandler (simulates UPI collect flow)
// - NetBankingPaymentHandler (simulates redirect flow)

// === FRAUD ===
public interface FraudRule {
    int evaluate(TransactionContext context);  // returns score points
    String getRuleName();
}

// === SETTLEMENT ===
public interface FeeStrategy {
    FeeResult calculateFee(BigDecimal amount, PaymentMethod method, MerchantFeeConfig config);
}
```

---

## 7. Key Design Pattern Implementations

### 7.1 State Machine (Payment)

```java
public enum PaymentState {
    CREATED, PROCESSING, AUTHORIZED, CAPTURED, SETTLED, VOIDED, REFUNDED, FAILED, EXPIRED
}

public enum PaymentEvent {
    AUTHORIZE, BANK_APPROVED, BANK_DECLINED, CAPTURE, VOID, REFUND, SETTLE, TIMEOUT
}

// Valid transitions map:
Map<PaymentState, Map<PaymentEvent, PaymentState>> transitions = Map.of(
    CREATED, Map.of(AUTHORIZE, PROCESSING, TIMEOUT, EXPIRED),
    PROCESSING, Map.of(BANK_APPROVED, AUTHORIZED, BANK_DECLINED, FAILED),
    AUTHORIZED, Map.of(CAPTURE, CAPTURED, VOID, VOIDED, TIMEOUT, EXPIRED),
    CAPTURED, Map.of(REFUND, REFUNDED, SETTLE, SETTLED)
);
```

### 7.2 Strategy Pattern (Routing)

```java
@Service
public class RoutingEngine {
    private final List<RoutingStrategy> strategies; // Injected by Spring
    
    public Route selectRoute(PaymentContext context) {
        return availableRoutes.stream()
            .map(route -> new ScoredRoute(route, calculateScore(route, context)))
            .max(Comparator.comparing(ScoredRoute::score))
            .map(ScoredRoute::route)
            .orElseThrow(() -> new NoRouteAvailableException());
    }
    
    private double calculateScore(Route route, PaymentContext context) {
        return strategies.stream()
            .mapToDouble(s -> s.scoreRoute(route, context))
            .average()
            .orElse(0.0);
    }
}
```

---

## 8. Interview Questions This Document Answers

1. **"Show me the class diagram for payment service"** → Section 2
2. **"How did you implement the state machine?"** → Section 7.1
3. **"How does the routing strategy work?"** → Section 7.2 (Strategy pattern)
4. **"How do different payment methods work?"** → PaymentMethodHandler interface
5. **"How does the webhook retry work?"** → Section 5 (RetryScheduler)
6. **"How does Spring Batch settlement work?"** → Section 4 (Reader/Processor/Writer)
7. **"What interfaces do you define?"** → Section 6

---

## Next Step

→ Continue to **`phase2-part3-database-schema-design.md`**
