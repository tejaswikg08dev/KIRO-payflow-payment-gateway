package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response wrapper used by ALL services.
 * 
 * Every REST endpoint returns data wrapped in this format:
 * 
 * Success:
 * {
 *   "success": true,
 *   "data": { ... },
 *   "timestamp": "2026-07-19T14:30:00Z"
 * }
 * 
 * Error:
 * {
 *   "success": false,
 *   "error": { "code": "...", "message": "..." },
 *   "timestamp": "2026-07-19T14:30:00Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields in JSON
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;
    private Instant timestamp;

    /**
     * Create a success response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a success response without data (e.g., delete operations).
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response.
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ErrorDetail(code, message, null))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error response with details.
     */
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ErrorDetail(code, message, details))
                .timestamp(Instant.now())
                .build();
    }
}
