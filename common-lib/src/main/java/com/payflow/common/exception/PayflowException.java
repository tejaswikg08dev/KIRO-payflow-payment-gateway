package com.payflow.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all PayFlow business errors.
 * All custom exceptions extend this.
 * 
 * Contains:
 * - errorCode: Machine-readable code (e.g., "PAYMENT_DECLINED")
 * - message: Human-readable description
 * - httpStatus: Which HTTP status to return (400, 404, 422, etc.)
 */
@Getter
public class PayflowException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public PayflowException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public PayflowException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
