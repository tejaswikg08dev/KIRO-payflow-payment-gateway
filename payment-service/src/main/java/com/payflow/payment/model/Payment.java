package com.payflow.payment.model;

import com.payflow.common.constant.PaymentMethod;
import com.payflow.common.constant.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", schema = "payment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "order_id", nullable = false, length = 50)
    private String orderId;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    // Card info (only last4 stored)
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_network", length = 20)
    private String cardNetwork;

    // UPI info
    @Column(name = "upi_vpa", length = 100)
    private String upiVpa;

    // Bank authorization details
    @Column(name = "auth_code", length = 10)
    private String authCode;

    @Column(length = 20)
    private String rrn;

    // Processing metadata
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "route_id", length = 50)
    private String routeId;

    // Amounts
    @Column(name = "captured_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    // Error info
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Timestamps
    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
