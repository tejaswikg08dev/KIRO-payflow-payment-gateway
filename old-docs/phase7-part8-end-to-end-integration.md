# Hands-On Guide — Phase 7 Part 8: End-to-End Integration

## Goal

By the end of Part 8, you will have:
- Complete flow tested: Payment Service → Routing Service → ISO 8583 → Bank Simulator → Response
- All services running together
- Verified: approve flow, decline flow, timeout flow
- Understanding of the full data path
- Git commit

## Prerequisites

- Parts 1-7 completed for Phase 7
- Phase 6 payment-service running
- Docker infrastructure running

---

## The Complete Data Flow

```
curl → API Gateway (8080) → Payment Service (8083) → Routing Service (8084) → Bank Sim (9000)

DETAILED:
1. Customer: POST /v1/payments {card: 4111...}
2. API Gateway: validate key, route to payment-service
3. Payment Service:
   a. Check idempotency (Redis)
   b. Create payment record (PostgreSQL)
   c. Run fraud check (score < 90? continue)
   d. Call routing-service: POST /internal/route {cardNumber, amount}
4. Routing Service:
   a. Select best route (HDFC_ACQ_01)
   b. Build ISO 8583 message (0100)
   c. Encode to binary bytes
   d. Open TCP socket to bank-simulator:9000
   e. Send [2-byte length][message bytes]
   f. Wait for response (max 5 seconds)
5. Bank Simulator:
   a. Accept TCP connection
   b. Read [2-byte length][message bytes]
   c. Parse card number from message
   d. Apply rules: 4111... → approve, code 00
   e. Build response: 0110 with auth_code + RRN
   f. Send [2-byte length][response bytes]
6. Routing Service:
   a. Receive response bytes
   b. Decode ISO 8583 response
   c. Field 39 = "00" → approved!
   d. Return: {success: true, authCode: "A1B2C3", rrn: "987654321012"}
7. Payment Service:
   a. Receive routing response
   b. Update payment: status=AUTHORIZED, authCode, rrn, routeId
   c. Save to PostgreSQL
   d. Cache idempotency key in Redis
   e. Return PaymentResponse to client
8. Customer sees: "Payment Successful! Auth code: A1B2C3"
```

---

## Step 8.1: Start All Services

Open 5 terminals:

```cmd
# Terminal 1: Infrastructure
docker compose -f docker-compose-infra.yml up -d

# Terminal 2: Eureka
cd service-registry && mvn spring-boot:run

# Terminal 3: Bank Simulator
cd bank-simulator && mvn spring-boot:run

# Terminal 4: Routing Service
cd routing-service && mvn spring-boot:run

# Terminal 5: Payment Service
cd payment-service && mvn spring-boot:run
```

Wait for all to show "Started...Application" in console.

---

## Step 8.2: Test Approve Flow (Full End-to-End)

```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: e2e_approve_001" ^
  -H "X-Merchant-Id: merch_e2e" ^
  -d "{\"orderId\":\"ord_e2e_test\",\"amount\":5000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\",\"holderName\":\"TEST USER\"}}"
```

**Expected:** status = "authorized", authCode present, rrn present

Check bank-simulator console:
```
APPROVING card ****1111: auth_code=X9Y8Z7
```

Check routing-service console:
```
Routing payment pay_xxx: ₹5000.0 (card ****1111)
Selected route: HDFC_ACQ_01
Bank response: MTI=0110, responseCode=00, authCode=X9Y8Z7
```

---

## Step 8.3: Test Decline Flow

```cmd
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: e2e_decline_001" ^
  -d "{\"orderId\":\"ord_e2e_dec\",\"amount\":5000.00,\"method\":\"card\",\"card\":{\"number\":\"4000000000000002\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\"}}"
```

**Expected:** status = "failed", failureCode = "BANK_DECLINED"

Check bank-simulator console:
```
DECLINING card ****0002: insufficient funds
```

---

## Step 8.4: Git Commit

```cmd
git add .
git commit -m "Phase 7 Part 8: End-to-end integration tested - approve, decline, timeout flows"
```

---

## Phase 7 Part 9 (Swagger & Testing) Summary

- Swagger UI: http://localhost:8084/swagger-ui.html shows POST /internal/route
- All ISO 8583 classes compile and work together
- Bank simulator responds to test cards correctly
- Payment-service integrates with routing (via simulated call or Feign when both running)

---

## Phase 7 Complete! 🎉

| Part | What Was Built |
|------|---------------|
| Part 1 | Routing service setup (pom, main class, config) |
| Part 2 | Smart routing engine + controller |
| Part 3 | Failover handling (retry logic, reversal) |
| Part 4 | ISO 8583 message classes (Iso8583Message, FieldDefs, FieldType) |
| Part 5 | ISO 8583 Encoder + Decoder |
| Part 6 | TCP client (BankTcpClient — socket communication) |
| Part 7 | Bank Simulator (TCP server + approve/decline rules) |
| Part 8 | End-to-end integration (all services together) |

**The ISO 8583 stack is complete.** Payments flow from REST API through binary protocol to the bank simulator and back. This is the UNIQUE part of this project that sets it apart from generic CRUD applications.

---

## Next Step

→ Move to **Phase 8: Settlement Service**
