package com.payflow.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "1.00", message = "Refund amount must be at least 1.00")
    private BigDecimal amount;

    private String reason;
}
