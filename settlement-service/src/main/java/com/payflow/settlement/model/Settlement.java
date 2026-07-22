package com.payflow.settlement.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "settlements", schema = "settlement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "refund_amount", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "gst_on_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal gstOnFee;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "total_transactions")
    @Builder.Default
    private int totalTransactions = 0;

    @Column(name = "total_refunds")
    @Builder.Default
    private int totalRefunds = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.INITIATED;

    @Column(name = "payout_utr", length = 50)
    private String payoutUtr;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SettlementStatus {
        INITIATED, PROCESSING, PROCESSED, COMPLETED, FAILED
    }
}
