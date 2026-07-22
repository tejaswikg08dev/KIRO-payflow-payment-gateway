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
    @Operation(summary = "Create a payment order", description = "Creates an order that expires in 30 minutes")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId) {
        String resolved = merchantId != null ? merchantId : "merch_default";
        OrderResponse response = orderService.createOrder(request, resolved);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String orderId) {
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
