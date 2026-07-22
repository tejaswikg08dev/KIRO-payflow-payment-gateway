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
public class RefundResponse {
    private String refundId;
    private String paymentId;
    private BigDecimal amount;
    private String status;
    private String reason;
    private String rrn;
    private Instant createdAt;
    private Instant processedAt;
}
