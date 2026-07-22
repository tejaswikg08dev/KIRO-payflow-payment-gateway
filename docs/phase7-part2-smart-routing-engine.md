# Hands-On Guide — Phase 7 Part 2: Smart Routing Engine

## Goal

By the end of Part 2, you will have:
- RoutingEngine class that selects the best bank for each payment
- RouteRequest/RouteResponse DTOs
- RoutingController with POST /internal/route endpoint
- Understanding of the multi-armed bandit routing algorithm
- Git commit

## Prerequisites

- Part 1 completed (routing-service starts on 8084)

---

## How Smart Routing Works (The Concept)

```
PROBLEM:
We have 3 banks we can route payments to:
├── HDFC Acquirer: success rate 97%, latency 180ms, cost 1.8%
├── ICICI Acquirer: success rate 89%, latency 250ms, cost 1.5%
└── Axis Acquirer: success rate 95%, latency 200ms, cost 2.0%

QUESTION: Which bank should we send THIS specific payment to?

ANSWER: Score each route, pick the highest:

  SCORING FORMULA:
  score = (success_rate × 0.5) + (1 - normalized_cost) × 0.3 + (1 - normalized_latency) × 0.2

  HDFC: (0.97 × 0.5) + (0.82 × 0.3) + (0.64 × 0.2) = 0.485 + 0.246 + 0.128 = 0.859
  ICICI: (0.89 × 0.5) + (0.85 × 0.3) + (0.50 × 0.2) = 0.445 + 0.255 + 0.100 = 0.800
  Axis:  (0.95 × 0.5) + (0.80 × 0.3) + (0.60 × 0.2) = 0.475 + 0.240 + 0.120 = 0.835

  WINNER: HDFC (score 0.859) ← route payment here

AI COMPONENT (Multi-Armed Bandit):
├── 90% of time: EXPLOIT (use best known route — HDFC)
├── 10% of time: EXPLORE (try another route — maybe ICICI improved!)
└── Over time: learns optimal routing for each card type, amount, time of day

FAILOVER:
├── Send to HDFC → bank declines/timeout
├── Try ICICI (second best) → success!
└── Update HDFC's failure count (affects future scoring)
```

---

## Step 2.1: Create Route DTOs

**Create file:** `routing-service/src/main/java/com/payflow/routing/dto/RouteRequest.java`

```java
package com.payflow.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request from payment-service to routing-service.
 * Contains all info needed to route a payment to a bank.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RouteRequest {

    private String paymentId;
    // Our payment ID (for logging/correlation)

    private String cardNumber;
    // Full 16-digit PAN — passed through to bank, NEVER logged or stored by routing
    // Example: "4111111111111111"

    private String cardExpiry;
    // Expiry in YYMM format: "2812" = December 2028

    private String cardLast4;
    // Last 4 digits for SAFE logging: "1111"
    // We log this, not the full number

    private long amount;
    // Transaction amount in PAISE (smallest currency unit)
    // ₹5000.00 = 500000 paise
    // Why paise? ISO 8583 uses smallest unit, avoids decimal math

    private String currency;
    // "INR" (ISO 4217)

    private String merchantId;
    // Which merchant this payment is for (for routing rules: big merchants get priority routes)
}
```

**Create file:** `routing-service/src/main/java/com/payflow/routing/dto/RouteResponse.java`

```java
package com.payflow.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from routing-service to payment-service.
 * Contains bank's decision (approve/decline) and reference numbers.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RouteResponse {

    private boolean success;
    // true = bank approved the payment
    // false = bank declined OR timeout OR routing error

    private String routeUsed;
    // Which bank we routed to: "HDFC_ACQ_01", "ICICI_ACQ_01", etc.
    // Stored on payment record for analytics

    private String responseCode;
    // ISO 8583 response code from bank:
    // "00" = approved, "51" = insufficient funds, "91" = bank unavailable

    private String authCode;
    // 6-char authorization code from bank (if approved): "A1B2C3"
    // This PROVES the bank approved — stored on payment, used for capture/reversal

    private String rrn;
    // 12-digit Retrieval Reference Number from bank: "987654321012"
    // Unique per transaction — used to track payment through banking system

    private String failureReason;
    // Human-readable explanation (if declined):
    // "Insufficient funds", "Card expired", "Bank timeout"
}
```

---

## Step 2.2: Create RoutingEngine

**Create file:** `routing-service/src/main/java/com/payflow/routing/service/RoutingEngine.java`

```java
package com.payflow.routing.service;

import com.payflow.routing.dto.RouteRequest;
import com.payflow.routing.dto.RouteResponse;
import com.payflow.routing.iso8583.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The Routing Engine — Decides which bank to use and communicates via ISO 8583.
 * 
 * This is the BRAIN of the routing service:
 * 1. Score available bank routes (AI routing)
 * 2. Pick the best one
 * 3. Build ISO 8583 message
 * 4. Send to bank via TCP
 * 5. Parse response
 * 6. If failed → failover to next route
 * 7. Update route metrics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingEngine {

    private final Iso8583Encoder encoder;
    // Converts Iso8583Message Java object → binary bytes

    private final Iso8583Decoder decoder;
    // Converts binary bytes → Iso8583Message Java object

    private final BankTcpClient bankClient;
    // Sends bytes to bank simulator via TCP, receives response

    /**
     * Main method: Route a payment to the best bank.
     * Called by payment-service via Feign: POST /internal/route
     */
    public RouteResponse routePayment(RouteRequest request) {
        log.info("Routing payment {}: ₹{} (card ****{})",
                request.getPaymentId(),
                request.getAmount() / 100.0,  // Convert paise to rupees for logging
                request.getCardLast4());

        // ===== STEP 1: Select Best Route =====
        String selectedRoute = selectBestRoute(request);
        log.info("Selected route: {}", selectedRoute);

        // ===== STEP 2: Build ISO 8583 Authorization Request (MTI 0100) =====
        Iso8583Message authRequest = buildAuthorizationRequest(request);
        log.debug("Built ISO 8583 message: {}", authRequest);

        // ===== STEP 3: Encode to binary bytes =====
        byte[] requestBytes = encoder.encode(authRequest);
        log.debug("Encoded: {} bytes", requestBytes.length);

        // ===== STEP 4: Send to bank via TCP =====
        byte[] responseBytes = bankClient.sendAndReceive(requestBytes);

        // ===== STEP 5: Handle timeout =====
        if (responseBytes == null) {
            log.warn("Bank TIMEOUT for payment {} (route: {})", 
                    request.getPaymentId(), selectedRoute);
            // TODO: Implement failover to next route
            // TODO: Send 0400 reversal to be safe
            return RouteResponse.builder()
                    .success(false)
                    .routeUsed(selectedRoute)
                    .responseCode("91") // Issuer not available
                    .failureReason("Bank did not respond within 5 seconds (timeout)")
                    .build();
        }

        // ===== STEP 6: Decode bank's response =====
        Iso8583Message authResponse = decoder.decode(responseBytes);
        log.info("Bank response: MTI={}, responseCode={}, authCode={}",
                authResponse.getMti(),
                authResponse.getResponseCode(),
                authResponse.getAuthCode());

        // ===== STEP 7: Build our response =====
        boolean approved = authResponse.isApproved(); // Field 39 == "00"

        return RouteResponse.builder()
                .success(approved)
                .routeUsed(selectedRoute)
                .responseCode(authResponse.getResponseCode())
                .authCode(approved ? authResponse.getAuthCode() : null)
                .rrn(authResponse.getRrn())
                .failureReason(approved ? null :
                        "Bank declined with response code: " + authResponse.getResponseCode())
                .build();
    }

    /**
     * Select the best bank route for this payment.
     * 
     * CURRENT: Simple selection (single route — HDFC)
     * FUTURE: Multi-armed bandit algorithm considering:
     *   - Historical success rate per bank
     *   - Average latency per bank
     *   - Cost per bank
     *   - Card network routing rules (Visa → Bank A, MC → Bank B)
     *   - Amount-based rules (large amounts → specific banks)
     */
    private String selectBestRoute(RouteRequest request) {
        // TODO: Implement multi-armed bandit scoring (Phase 10 - AI)
        // For now: always route to HDFC (our single simulated bank)
        return "HDFC_ACQ_01";
    }

    /**
     * Build ISO 8583 Authorization Request message (MTI 0100).
     * This is the message format that ALL card networks worldwide use.
     */
    private Iso8583Message buildAuthorizationRequest(RouteRequest request) {
        Iso8583Message msg = new Iso8583Message("0100");
        // MTI 0100 = "Authorization Request from Acquirer"

        LocalDateTime now = LocalDateTime.now();

        // Field 2: PAN (Primary Account Number = card number)
        msg.setField(2, request.getCardNumber());

        // Field 3: Processing Code (000000 = purchase)
        msg.setField(3, "000000");

        // Field 4: Amount (12 digits, in smallest currency unit = paise)
        msg.setField(4, String.format("%012d", request.getAmount()));
        // ₹5000.00 → 500000 → "000000500000"

        // Field 7: Transmission date/time (MMDDhhmmss)
        msg.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));

        // Field 11: STAN (System Trace Audit Number — unique per transaction)
        msg.setField(11, generateStan());

        // Field 12: Local time (hhmmss)
        msg.setField(12, now.format(DateTimeFormatter.ofPattern("HHmmss")));

        // Field 13: Local date (MMDD)
        msg.setField(13, now.format(DateTimeFormatter.ofPattern("MMdd")));

        // Field 14: Card expiry (YYMM)
        msg.setField(14, request.getCardExpiry());

        // Field 22: POS Entry Mode (081 = e-commerce/online)
        msg.setField(22, "081");

        // Field 25: POS Condition Code (00 = normal transaction)
        msg.setField(25, "00");

        // Field 32: Acquiring Institution ID (our bank ID)
        msg.setField(32, "12345678");

        // Field 41: Terminal ID (our system identifier)
        msg.setField(41, "TERM0001");

        // Field 42: Merchant ID (padded to 15 chars)
        msg.setField(42, padRight(request.getMerchantId(), 15));

        // Field 43: Merchant Name + Location (padded to 40 chars)
        msg.setField(43, padRight("PayFlow Merchant Mumbai IN", 40));

        // Field 49: Currency Code (356 = INR)
        msg.setField(49, "356");

        return msg;
    }

    private String generateStan() {
        return String.format("%06d", (int) (Math.random() * 999999));
        // Random 6-digit number (unique enough for testing)
        // Production: sequential counter per day, reset at midnight
    }

    private String padRight(String str, int length) {
        if (str == null) str = "";
        return String.format("%-" + length + "s", str).substring(0, length);
        // Pad with spaces on the right to exact length
        // "TERM0001" (8 chars) → stays "TERM0001"
        // "merch_xyz" (9 chars) → "merch_xyz      " (padded to 15)
    }
}
```

---

## Step 2.3: Create RoutingController

**Create file:** `routing-service/src/main/java/com/payflow/routing/controller/RoutingController.java`

```java
package com.payflow.routing.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.routing.dto.RouteRequest;
import com.payflow.routing.dto.RouteResponse;
import com.payflow.routing.service.RoutingEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/route")
// "/internal/" prefix indicates: this is NOT for external merchants
// Only called by payment-service via Feign (service-to-service)
@RequiredArgsConstructor
@Tag(name = "Routing (Internal)", description = "Routes payments to banks via ISO 8583. Called by payment-service only.")
public class RoutingController {

    private final RoutingEngine routingEngine;

    @PostMapping
    @Operation(summary = "Route a payment to the best bank",
            description = "Selects optimal bank, builds ISO 8583 message, sends via TCP, "
                + "returns bank's response (approved/declined + auth code + RRN)")
    public ResponseEntity<ApiResponse<RouteResponse>> routePayment(
            @RequestBody RouteRequest request) {

        RouteResponse response = routingEngine.routePayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

## Step 2.4: Verify (Without Bank Simulator)

Start routing-service (it will fail to connect to bank, but endpoint is accessible):

```cmd
cd routing-service
mvn spring-boot:run
```

Test endpoint (will get connection error since bank-simulator isn't running yet):
```cmd
curl -X POST http://localhost:8084/internal/route ^
  -H "Content-Type: application/json" ^
  -d "{\"paymentId\":\"pay_test\",\"cardNumber\":\"4111111111111111\",\"cardExpiry\":\"2812\",\"cardLast4\":\"1111\",\"amount\":500000,\"currency\":\"INR\",\"merchantId\":\"merch_test\"}"
```

**Expected:** Error response (bank simulator not running yet — that's Part 7).

---

## Step 2.5: Git Commit

```cmd
git add routing-service/src/main/java/com/payflow/routing/dto/
git add routing-service/src/main/java/com/payflow/routing/service/RoutingEngine.java
git add routing-service/src/main/java/com/payflow/routing/controller/
git commit -m "Phase 7 Part 2: Routing engine + controller (route selection, ISO 8583 message building)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `dto/RouteRequest.java` | Input from payment-service (card, amount, merchant) |
| `dto/RouteResponse.java` | Output to payment-service (approved/declined, auth code) |
| `service/RoutingEngine.java` | Core logic: select route → build ISO → send → parse |
| `controller/RoutingController.java` | POST /internal/route endpoint |

---

## Interview Notes

**Q: "How does your payment routing work?"**
> "The routing engine scores available bank routes based on historical success rate (50% weight), cost (30%), and latency (20%). It uses a multi-armed bandit approach — 90% of the time it picks the best known route, 10% it explores alternatives to discover if a previously bad route has improved. If the selected route fails, it failover to the next best."

**Q: "Why is routing a separate service?"**
> "Separation of concerns. Payment-service handles business logic (state machine, idempotency). Routing-service handles bank communication (ISO 8583 protocol, TCP, failover). If we add a new bank or the protocol changes, only routing-service needs updating. They have different scaling needs — routing is CPU/network bound, payment is DB bound."

---

## Next Step

→ Continue to **Phase 7 Part 3: Failover Handling**
