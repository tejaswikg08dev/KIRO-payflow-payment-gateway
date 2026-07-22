package com.payflow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String method;

    // Card details (masked)
    private String cardLast4;
    private String cardNetwork;

    // UPI details
    private String upiVpa;

    // Bank response
    private String authCode;
    private String rrn;

    // Risk
    private Integer riskScore;
    private String routeUsed;

    // Amounts
    private BigDecimal capturedAmount;
    private BigDecimal refundedAmount;

    // Error
    private String failureCode;
    private String failureReason;

    // Timestamps
    private Instant authorizedAt;
    private Instant capturedAt;
    private Instant createdAt;
}
