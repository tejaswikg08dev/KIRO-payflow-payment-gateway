package com.payflow.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private boolean success;       // true = bank approved
    private String routeUsed;      // "HDFC_ACQ_01"
    private String responseCode;   // "00" = approved, "51" = insufficient funds
    private String authCode;       // Authorization code from bank (if approved)
    private String rrn;            // Retrieval Reference Number from bank
    private String failureReason;  // Human-readable failure message (if declined)
}
