package com.payflow.merchant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "merchants", schema = "merchant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "business_type", nullable = false, length = 50)
    private String businessType;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "settlement_schedule", length = 10, nullable = false)
    @Builder.Default
    private String settlementSchedule = "T+2";

    @Column(name = "mdr_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal mdrPercentage = new BigDecimal("2.00");

    @Column(name = "bank_account_number", length = 30)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc_code", length = 15)
    private String bankIfscCode;

    @Column(name = "bank_account_holder", length = 200)
    private String bankAccountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING;

    @Column(name = "kyc_verified", nullable = false)
    @Builder.Default
    private boolean kycVerified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum MerchantStatus {
        PENDING, ACTIVE, SUSPENDED
    }
}
