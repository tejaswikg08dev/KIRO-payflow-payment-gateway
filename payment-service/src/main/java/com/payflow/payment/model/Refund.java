package com.payflow.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds", schema = "payment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "payment_id", nullable = false, length = 50)
    private String paymentId;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.INITIATED;

    @Column(length = 500)
    private String reason;

    @Column(length = 20)
    private String rrn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum RefundStatus {
        INITIATED, PROCESSED, FAILED
    }
}
