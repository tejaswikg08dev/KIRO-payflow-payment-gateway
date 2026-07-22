package com.payflow.payment.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.payment.dto.CaptureRequest;
import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.service.PaymentProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment authorization, capture, void, and refund")
public class PaymentController {

    private final PaymentProcessorService paymentProcessor;

    @PostMapping
    @Operation(summary = "Create and authorize a payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId) {

        PaymentResponse response = paymentProcessor.processPayment(request, idempotencyKey, merchantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{paymentId}/capture")
    @Operation(summary = "Capture an authorized payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> capturePayment(
            @PathVariable String paymentId,
            @RequestBody(required = false) CaptureRequest request) {

        PaymentResponse response = paymentProcessor.capturePayment(paymentId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{paymentId}/void")
    @Operation(summary = "Void an authorized payment (release hold)")
    public ResponseEntity<ApiResponse<PaymentResponse>> voidPayment(@PathVariable String paymentId) {
        PaymentResponse response = paymentProcessor.voidPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment details by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable String paymentId) {
        PaymentResponse response = paymentProcessor.getPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
