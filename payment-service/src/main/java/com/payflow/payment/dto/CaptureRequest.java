package com.payflow.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptureRequest {
    private BigDecimal amount; // Optional: if null, capture full authorized amount
}
