# Hands-On Guide — Phase 6 Part 3: Order Creation

## Goal

By the end of Part 3, you will have:
- OrderService with createOrder() logic
- OrderController with POST /v1/orders and GET /v1/orders/{id}
- Orders auto-expire after 30 minutes
- Working order creation tested with curl
- Git commit

## Prerequisites

- Part 2 completed (state machine class exists)
- payment-service starts without errors

---

## What Is an Order?

```
An ORDER is a payment intent — it says:
"A customer wants to pay ₹5000 to merchant X"

The ORDER exists BEFORE the customer enters card details.

FLOW:
1. Customer adds items to cart on merchant's website
2. Customer clicks "Checkout"
3. Merchant's server calls OUR API:
   POST /v1/orders { amount: 5000, currency: "INR", receipt: "cart_123" }
4. We create order: ord_LkR3d9xF2m (status: CREATED, expires in 30 min)
5. Merchant redirects customer to our hosted checkout page
6. Customer sees: "Pay ₹5000 to TechShop" and enters card details
7. Customer submits → POST /v1/payments (references this order)
8. If payment succeeds → order status: PAID
9. If 30 minutes pass without payment → order status: EXPIRED

WHY SEPARATE ORDER FROM PAYMENT?
├── One order can have MULTIPLE payment attempts
│   (customer's first card declines → they try another card)
├── Merchant creates order ONCE, regardless of how many tries
├── Order has the "what" (amount, merchant) — payment has the "how" (card details)
└── Order expiry prevents abandoned checkouts from piling up
```

---

## Step 3.1: Create OrderRequest DTO

**Create file:** `payment-service/src/main/java/com/payflow/payment/dto/OrderRequest.java`

```java
package com.payflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least ₹1.00")
    private BigDecimal amount;
    // The total amount customer needs to pay
    // In smallest displayable unit (rupees, not paise)
    // Example: 5000.00 = ₹5,000

    private String currency;
    // ISO 4217: "INR" (default), "USD", "EUR"
    // If null, defaults to INR

    private String receipt;
    // Merchant's internal reference (their order ID)
    // Example: "ORDER-12345", "cart_abc"
    // We store it but don't use it — helps merchant correlate

    private Map<String, Object> notes;
    // Flexible metadata (any key-value pairs)
    // Example: { "product": "Laptop", "customer_email": "buyer@gmail.com" }
    // Stored as JSONB in PostgreSQL
}
```

---

## Step 3.2: Create OrderResponse DTO

**Create file:** `payment-service/src/main/java/com/payflow/payment/dto/OrderResponse.java`

```java
package com.payflow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String receipt;
    private String status;           // created, paid, expired
    private Map<String, Object> notes;
    private Instant expiresAt;       // When this order will auto-expire
    private Instant paidAt;          // When payment succeeded (null if not yet)
    private Instant createdAt;
}
```

---

## Step 3.3: Create OrderRepository

**Create file:** `payment-service/src/main/java/com/payflow/payment/repository/OrderRepository.java`

```java
package com.payflow.payment.repository;

import com.payflow.payment.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByMerchantIdAndStatus(String merchantId, Order.OrderStatus status);
    // Used by: "Find all active orders for this merchant"

    List<Order> findByStatusAndExpiresAtBefore(Order.OrderStatus status, Instant before);
    // Used by: Expiry scheduler job
    // "Find all CREATED orders where expires_at < NOW()" → mark as EXPIRED
}
```

---

## Step 3.4: Create OrderService

**Create file:** `payment-service/src/main/java/com/payflow/payment/service/OrderService.java`

```java
package com.payflow.payment.service;

import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.payment.dto.OrderRequest;
import com.payflow.payment.dto.OrderResponse;
import com.payflow.payment.model.Order;
import com.payflow.payment.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Value("${payment.order-expiry-minutes:30}")
    private int orderExpiryMinutes;
    // Read from application.yml: payment.order-expiry-minutes = 30

    /**
     * Create a new payment order.
     * 
     * This is step 1 of payment flow:
     * Merchant calls this → gets order_id → customer uses order_id to pay
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String merchantId) {
        Order order = Order.builder()
                .id(IdGenerator.orderId())
                // Generate: "ord_LkR3d9xF2m"
                .merchantId(merchantId)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .receipt(request.getReceipt())
                .notes(request.getNotes())
                .status(Order.OrderStatus.CREATED)
                .expiresAt(Instant.now().plus(orderExpiryMinutes, ChronoUnit.MINUTES))
                // Expires 30 minutes from now
                // If customer doesn't pay by then → auto-expires
                .build();

        orderRepository.save(order);
        log.info("Order created: {} (₹{}) for merchant {}", 
                order.getId(), order.getAmount(), merchantId);

        return toResponse(order);
    }

    /**
     * Get order by ID.
     */
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return toResponse(order);
    }

    /**
     * Mark order as paid (called internally when payment succeeds).
     */
    @Transactional
    public void markAsPaid(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .receipt(order.getReceipt())
                .status(order.getStatus().name().toLowerCase())
                .notes(order.getNotes())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
```

---

## Step 3.5: Create OrderController

**Create file:** `payment-service/src/main/java/com/payflow/payment/controller/OrderController.java`

```java
package com.payflow.payment.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.payment.dto.OrderRequest;
import com.payflow.payment.dto.OrderResponse;
import com.payflow.payment.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Payment order management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Create a payment order",
            description = "Creates an order with amount and currency. "
                + "Returns order_id that customer will use to pay. Expires in 30 minutes.")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId) {
        // X-Merchant-Id comes from API Gateway after validating the API key
        // For local testing without gateway, we accept it as optional
        String resolvedMerchantId = merchantId != null ? merchantId : "merch_default";

        OrderResponse response = orderService.createOrder(request, resolvedMerchantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable String orderId) {
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

## Step 3.6: Verify with curl

### Create an order:
```cmd
curl -X POST http://localhost:8083/v1/orders ^
  -H "Content-Type: application/json" ^
  -H "X-Merchant-Id: merch_test123" ^
  -d "{\"amount\":5000.00,\"currency\":\"INR\",\"receipt\":\"cart_abc123\",\"notes\":{\"product\":\"Wireless Headphones\"}}"
```

**Expected (201):**
```json
{
  "success": true,
  "data": {
    "orderId": "ord_LkR3d9xF2m",
    "amount": 5000.00,
    "currency": "INR",
    "receipt": "cart_abc123",
    "status": "created",
    "notes": { "product": "Wireless Headphones" },
    "expiresAt": "2026-07-20T11:30:00Z",
    "createdAt": "2026-07-20T11:00:00Z"
  }
}
```

### Get order:
```cmd
curl http://localhost:8083/v1/orders/ord_LkR3d9xF2m
```

---

## Step 3.7: Git Commit

```cmd
git add payment-service/src/main/java/com/payflow/payment/dto/OrderRequest.java
git add payment-service/src/main/java/com/payflow/payment/dto/OrderResponse.java
git add payment-service/src/main/java/com/payflow/payment/repository/OrderRepository.java
git add payment-service/src/main/java/com/payflow/payment/service/OrderService.java
git add payment-service/src/main/java/com/payflow/payment/controller/OrderController.java
git commit -m "Phase 6 Part 3: Order creation - POST /v1/orders + GET /v1/orders/{id}"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `dto/OrderRequest.java` | Input: amount, currency, receipt, notes |
| `dto/OrderResponse.java` | Output: orderId, status, expiresAt |
| `repository/OrderRepository.java` | DB access for orders |
| `service/OrderService.java` | Create order, get order, mark as paid |
| `controller/OrderController.java` | POST /v1/orders, GET /v1/orders/{id} |

---

## Next Step

→ Continue to **Phase 6 Part 4: Payment Authorization**

In Part 4, we implement the core payment flow — customer submits card/UPI details, we authorize with the bank, return approved/declined.
