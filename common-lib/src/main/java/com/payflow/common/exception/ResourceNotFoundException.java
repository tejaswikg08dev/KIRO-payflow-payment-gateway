package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource is not found.
 * Returns HTTP 404.
 * 
 * Example usage:
 *   throw new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment with ID pay_xyz not found");
 */
public class ResourceNotFoundException extends PayflowException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(
                resourceName.toUpperCase() + "_NOT_FOUND",
                String.format("%s with %s '%s' not found", resourceName, fieldName, fieldValue),
                HttpStatus.NOT_FOUND
        );
    }
}
