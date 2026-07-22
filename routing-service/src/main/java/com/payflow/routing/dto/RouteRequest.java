package com.payflow.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {
    private String paymentId;
    private String cardNumber;      // Full PAN (passed through, never stored by routing)
    private String cardExpiry;      // YYMM format
    private String cardLast4;       // For logging (safe)
    private long amount;            // In paise (₹50.00 = 5000)
    private String currency;        // "INR"
    private String merchantId;
    private String merchantName;
}
