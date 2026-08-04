# Hands-On Guide — Phase 7 Part 3: Failover Handling

## Goal

By the end of Part 3, you will have:
- Understanding of circuit breaker pattern for bank connections
- Failover logic: if Bank A fails → try Bank B
- Timeout handling: what to do when bank doesn't respond
- Reversal strategy (send 0400 when unsure)
- Git commit

## Prerequisites

- Part 2 completed (RoutingEngine exists)

---

## What Is Failover?

```
SCENARIO: We route to HDFC bank, but HDFC is having issues:

WITHOUT FAILOVER:
  Payment → Route to HDFC → TIMEOUT (5 sec) → Tell customer "FAILED"
  → Customer upset, merchant loses sale 😞

WITH FAILOVER:
  Payment → Route to HDFC → TIMEOUT (5 sec)
         → Send reversal (0400) to HDFC (safety: in case they did process it)
         → Try ICICI (next best route) → SUCCESS!
         → Customer sees "Payment Successful!" ✅

RULES FOR FAILOVER:
├── Only failover on RETRYABLE errors:
│   ├── Timeout (bank didn't respond) → RETRY on different bank
│   ├── Response code 91 (bank unavailable) → RETRY
│   ├── Response code 96 (system malfunction) → RETRY
│   └── Connection refused (bank server down) → RETRY
│
└── Do NOT failover on PERMANENT errors:
    ├── Response code 51 (insufficient funds) → DECLINE (don't try another bank)
    ├── Response code 54 (expired card) → DECLINE
    ├── Response code 14 (invalid card) → DECLINE
    └── Response code 05 (do not honor) → DECLINE

WHY? Because "insufficient funds" means the customer's account doesn't have money.
Trying another bank won't fix that — the card is the same!
```

---

## Step 3.1: Identify Retryable vs Non-Retryable Response Codes

```java
/**
 * Determines if a bank response code is retryable on a different route.
 * Retryable = bank/network issue (might work on another bank)
 * Non-retryable = customer/card issue (same result on any bank)
 */
public static boolean isRetryableResponseCode(String responseCode) {
    // Retryable: bank/infrastructure issues
    return switch (responseCode) {
        case "91" -> true;   // Issuer/switch not available
        case "96" -> true;   // System malfunction
        case "68" -> true;   // Response received too late
        case "30" -> true;   // Format error (might be bank-specific)
        default -> false;    // All others: permanent decline
    };
}
```

---

## Step 3.2: Failover in RoutingEngine

The failover logic fits into `RoutingEngine.routePayment()`:

```java
public RouteResponse routePayment(RouteRequest request) {
    // Get ordered list of routes (best first)
    List<String> routes = getOrderedRoutes(request);
    // Example: ["HDFC_ACQ_01", "AXIS_ACQ_01", "ICICI_ACQ_01"]

    for (int attempt = 0; attempt < routes.size(); attempt++) {
        String route = routes.get(attempt);
        log.info("Attempt {}: routing to {} for payment {}",
                attempt + 1, route, request.getPaymentId());

        // Build and send ISO 8583
        Iso8583Message authRequest = buildAuthorizationRequest(request);
        byte[] requestBytes = encoder.encode(authRequest);
        byte[] responseBytes = bankClient.sendAndReceive(requestBytes);

        // TIMEOUT: bank didn't respond
        if (responseBytes == null) {
            log.warn("Timeout on route {} (attempt {})", route, attempt + 1);
            // Send reversal (safety: if bank processed but response was lost)
            sendReversalAsync(request, route);
            continue; // Try next route
        }

        // Parse response
        Iso8583Message response = decoder.decode(responseBytes);
        String responseCode = response.getResponseCode();

        // APPROVED
        if ("00".equals(responseCode)) {
            return RouteResponse.builder()
                    .success(true)
                    .routeUsed(route)
                    .responseCode(responseCode)
                    .authCode(response.getAuthCode())
                    .rrn(response.getRrn())
                    .build();
        }

        // RETRYABLE DECLINE: try next bank
        if (isRetryableResponseCode(responseCode)) {
            log.warn("Retryable decline on route {}: code={}", route, responseCode);
            continue; // Try next route
        }

        // PERMANENT DECLINE: don't retry (card issue, not bank issue)
        log.info("Permanent decline on route {}: code={}", route, responseCode);
        return RouteResponse.builder()
                .success(false)
                .routeUsed(route)
                .responseCode(responseCode)
                .failureReason(getDeclineReason(responseCode))
                .build();
    }

    // ALL routes failed
    log.error("All routes exhausted for payment {}", request.getPaymentId());
    return RouteResponse.builder()
            .success(false)
            .routeUsed("ALL_ROUTES_FAILED")
            .responseCode("91")
            .failureReason("All bank routes are currently unavailable. Please try again later.")
            .build();
}
```

---

## Step 3.3: Timeout & Reversal Strategy

```
WHAT HAPPENS ON TIMEOUT:

We sent 0100 (auth request) to bank...
...5 seconds pass, no response...

PROBLEM: Did the bank process it or not?
├── MAYBE the bank approved it (money held) but response was lost in network
├── MAYBE the bank is still processing (slow day)
├── MAYBE the bank crashed before processing

SAFEST ACTION: Send 0400 (reversal request)
├── If bank DID process it → reversal cancels the auth (hold released)
├── If bank DIDN'T process it → reversal has no effect (nothing to reverse)
└── Either way: customer is NOT charged, and we retry on another bank

This is called "reversal on timeout" — standard practice in all payment systems.
```

```java
/**
 * Send a reversal asynchronously (don't block the failover flow).
 * If bank processed the original but we timed out waiting for response,
 * the reversal ensures customer isn't charged.
 */
private void sendReversalAsync(RouteRequest request, String route) {
    // Build 0400 reversal message
    Iso8583Message reversal = new Iso8583Message("0400");
    reversal.setField(2, request.getCardNumber());
    reversal.setField(3, "000000");
    reversal.setField(4, String.format("%012d", request.getAmount()));
    reversal.setField(11, generateStan());
    reversal.setField(41, "TERM0001");
    reversal.setField(42, padRight(request.getMerchantId(), 15));
    reversal.setField(49, "356");

    // Send async (don't wait for response — it's a safety measure)
    new Thread(() -> {
        byte[] bytes = encoder.encode(reversal);
        byte[] resp = bankClient.sendAndReceive(bytes);
        if (resp != null) {
            Iso8583Message reversalResp = decoder.decode(resp);
            log.info("Reversal response for {}: code={}", 
                    request.getPaymentId(), reversalResp.getResponseCode());
        }
    }).start();
}
```

---

## Step 3.4: Git Commit

```cmd
git add routing-service/
git commit -m "Phase 7 Part 3: Failover logic - retry on retryable codes, reversal on timeout"
```

---

## What We Built

| Concept | Implementation |
|---------|---------------|
| Failover | Try next bank if current bank times out or returns retryable code |
| Retryable codes | 91, 96, 68, 30 (bank issues) |
| Non-retryable | 51, 54, 14, 05 (card/customer issues) |
| Reversal on timeout | Send 0400 async before trying next bank |
| All routes failed | Return controlled error after exhausting all banks |

---

## Interview Notes

**Q: "What happens if the bank times out?"**
> "We send a reversal (ISO 8583 0400) asynchronously to the timed-out bank — in case it processed the auth but the response was lost. Then we failover to the next best bank route. If all routes fail, we return a clear error. This ensures the customer is never double-charged."

**Q: "How do you decide when to retry vs when to give up?"**
> "We only retry on retryable response codes — these indicate bank/infrastructure issues (91 = unavailable, 96 = malfunction). We never retry on permanent declines (51 = insufficient funds, 54 = expired) because trying another bank won't help — the card itself is the issue."

---

## Next Step

→ Continue to **Phase 7 Part 4: ISO 8583 Message Classes**
