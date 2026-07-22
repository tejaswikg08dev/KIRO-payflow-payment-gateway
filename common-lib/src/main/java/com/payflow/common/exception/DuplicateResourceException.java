package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when trying to create a resource that already exists.
 * Returns HTTP 409 Conflict.
 * 
 * Example: Registering with an email that's already taken.
 */
public class DuplicateResourceException extends PayflowException {

    public DuplicateResourceException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }
}
