# Hands-On Guide — Phase 6 Part 8: Feign Clients & Service Integration

## Goal

By the end of Part 8, you will have:
- RoutingServiceClient (Feign interface to call routing-service)
- Understanding of how services call each other in microservices
- Circuit breaker protecting against routing-service failures
- Payment service calls routing for bank authorization (Phase 7 completes this)
- Git commit

## Prerequisites

- Part 7 completed (idempotency working)
- Understanding of what routing-service does (Phase 7 builds it)

---

## How Services Communicate

```
IN A MONOLITH:
PaymentService calls RoutingService directly (same JVM, same process):
  routingService.routePayment(request); // Simple method call

IN MICROSERVICES:
PaymentService (port 8083) calls RoutingService (port 8084) via HTTP:
  POST http://routing-service:8084/internal/route {paymentId, cardNumber, amount}
  
PROBLEM: Writing HTTP client code is verbose and repetitive:
  RestTemplate restTemplate = new RestTemplate();
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.APPLICATION_JSON);
  HttpEntity<RouteRequest> entity = new HttpEntity<>(request, headers);
  ResponseEntity<RouteResponse> response = restTemplate.exchange(
      "http://routing-service:8084/internal/route",
      HttpMethod.POST, entity, RouteResponse.class);
  // 5 lines just to make one HTTP call! 😩

SOLUTION: OpenFeign (declarative HTTP client):
  @FeignClient(name = "ROUTING-SERVICE")
  public interface RoutingServiceClient {
      @PostMapping("/internal/route")
      ApiResponse<RouteResponse> routePayment(@RequestBody RouteRequest request);
  }
  // ONE INTERFACE! Spring generates all HTTP code at runtime! ✨
  // Call it like a normal method: routingClient.routePayment(request);
```

---

## Step 8.1: Create RoutingServiceClient (Feign)

**Create file:** `payment-service/src/main/java/com/payflow/payment/client/RoutingServiceClient.java`

```java
package com.payflow.payment.client;

import com.payflow.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for calling Routing Service.
 * 
 * @FeignClient(name = "ROUTING-SERVICE"):
 * - "ROUTING-SERVICE" is the name in Eureka registry
 * - Spring asks Eureka: "Where is ROUTING-SERVICE?" → gets IP:port
 * - Automatically load-balances if multiple instances exist
 * 
 * You NEVER write HTTP client code yourself!
 * Just define the interface — Spring generates the implementation.
 */
@FeignClient(
    name = "ROUTING-SERVICE",
    // Eureka service name (routing-service registers as "ROUTING-SERVICE")
    fallback = RoutingServiceFallback.class
    // If routing-service is DOWN, use this fallback instead of crashing
)
public interface RoutingServiceClient {

    @PostMapping("/internal/route")
    // Equivalent to: POST http://routing-service:8084/internal/route
    ApiResponse<RouteResponse> routePayment(@RequestBody RouteRequest request);
    // Spring generates: serialize request → HTTP POST → deserialize response
    // All error handling, retries, timeouts configured separately
}
```

---

## Step 8.2: Create DTOs for Routing Communication

**Create file:** `payment-service/src/main/java/com/payflow/payment/client/RouteRequest.java`

```java
package com.payflow.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RouteRequest {
    private String paymentId;
    private String cardNumber;    // Full PAN (passed through to bank, never stored)
    private String cardExpiry;    // YYMM
    private String cardLast4;     // For logging only
    private long amount;          // In paise (₹50.00 = 5000)
    private String currency;
    private String merchantId;
}
```

**Create file:** `payment-service/src/main/java/com/payflow/payment/client/RouteResponse.java`

```java
package com.payflow.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RouteResponse {
    private boolean success;       // true = approved by bank
    private String routeUsed;      // "HDFC_ACQ_01"
    private String responseCode;   // "00" = approved
    private String authCode;       // "A1B2C3" (bank's auth code)
    private String rrn;            // "987654321012" (bank's reference)
    private String failureReason;  // null if success, "insufficient funds" if declined
}
```

---

## Step 8.3: Create Fallback (When Routing Service is Down)

**Create file:** `payment-service/src/main/java/com/payflow/payment/client/RoutingServiceFallback.java`

```java
package com.payflow.payment.client;

import com.payflow.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when routing-service is DOWN or circuit breaker is OPEN.
 * 
 * Instead of crashing, we return a controlled failure response.
 * The payment is marked as FAILED with a clear reason.
 */
@Slf4j
@Component
public class RoutingServiceFallback implements RoutingServiceClient {

    @Override
    public ApiResponse<RouteResponse> routePayment(RouteRequest request) {
        log.error("FALLBACK: Routing service unavailable for payment {}", request.getPaymentId());
        
        RouteResponse fallbackResponse = RouteResponse.builder()
                .success(false)
                .responseCode("91")  // 91 = "Issuer/switch not available"
                .failureReason("Payment routing service is temporarily unavailable. Please try again.")
                .build();

        return ApiResponse.success(fallbackResponse);
        // Returns a "success" API response containing a "failed" route response
        // This way Feign doesn't throw an exception — we handle it gracefully
    }
}
```

---

## Step 8.4: How This Integrates with PaymentProcessorService

In Phase 7, we'll replace `simulateBankAuthorization()` with:

```java
private void routeToBankViaFeignClient(Payment payment, PaymentRequest request) {
    RouteRequest routeRequest = RouteRequest.builder()
            .paymentId(payment.getId())
            .cardNumber(request.getCard().getNumber())
            .cardExpiry(formatExpiry(request.getCard()))
            .cardLast4(payment.getCardLast4())
            .amount(payment.getAmount().multiply(new BigDecimal("100")).longValue())
            .currency(payment.getCurrency())
            .merchantId(payment.getMerchantId())
            .build();

    ApiResponse<RouteResponse> response = routingClient.routePayment(routeRequest);
    RouteResponse route = response.getData();

    if (route.isSuccess()) {
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthCode(route.getAuthCode());
        payment.setRrn(route.getRrn());
        payment.setRouteId(route.getRouteUsed());
        payment.setAuthorizedAt(Instant.now());
    } else {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureCode("BANK_DECLINED");
        payment.setFailureReason(route.getFailureReason());
    }
}
```

---

## Step 8.5: Git Commit

```cmd
git add payment-service/src/main/java/com/payflow/payment/client/
git commit -m "Phase 6 Part 8: Feign clients for routing-service integration + fallback"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `client/RoutingServiceClient.java` | Feign interface to call routing-service |
| `client/RouteRequest.java` | DTO sent to routing |
| `client/RouteResponse.java` | DTO received from routing |
| `client/RoutingServiceFallback.java` | Graceful failure when routing is down |

---

## Interview Notes

**Q: "How do your services communicate?"**
> "Synchronous calls use OpenFeign — I define an interface with the endpoint, and Spring generates the HTTP client code. Feign integrates with Eureka for service discovery and Resilience4j for circuit breaking. Async communication uses SQS for events that don't need immediate response."

**Q: "What happens if a downstream service is down?"**
> "Circuit breaker pattern via Resilience4j. After 5 consecutive failures, the circuit opens — requests immediately fail without even trying to connect. After 30 seconds, it half-opens (allows 3 test requests). If they succeed, circuit closes. We also have Feign fallbacks that return controlled error responses."

---

## Next Step

→ Continue to **Phase 6 Part 9: Controllers, Swagger & Full Testing**
