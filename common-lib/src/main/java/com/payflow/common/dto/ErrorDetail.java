package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error detail returned in API error responses.
 * 
 * Example JSON:
 * {
 *   "code": "PAYMENT_DECLINED",
 *   "message": "Payment was declined due to insufficient funds",
 *   "details": { "bank_response_code": "51" }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    /** Machine-readable error code (e.g., "PAYMENT_DECLINED", "INVALID_API_KEY") */
    private String code;

    /** Human-readable error message */
    private String message;

    /** Additional context (optional) — can be any object */
    private Object details;
}
