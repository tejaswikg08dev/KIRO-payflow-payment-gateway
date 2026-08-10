package com.payflow.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /v1/auth/profile endpoint.
 * Returns user info extracted from JWT token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private String userId;
    private String email;
    private String role;
    private String merchantId;  // Will be populated by frontend after calling Merchant Service
}
