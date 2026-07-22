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
    private String status;
    private Map<String, Object> notes;
    private Instant expiresAt;
    private Instant paidAt;
    private Instant createdAt;
}
