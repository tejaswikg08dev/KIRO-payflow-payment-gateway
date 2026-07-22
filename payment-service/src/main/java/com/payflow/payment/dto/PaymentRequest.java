package com.payflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    private BigDecimal amount;

    private String currency; // defaults to INR

    @NotBlank(message = "Payment method is required (card, upi, netbanking)")
    private String method;

    // Card payment details
    private CardDetails card;

    // UPI payment details
    private UpiDetails upi;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardDetails {
        private String number;
        private int expiryMonth;
        private int expiryYear;
        private String cvv;
        private String holderName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpiDetails {
        private String vpa;
    }
}
