package com.payflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting an invalid state transition on a payment.
 * Returns HTTP 400 Bad Request.
 * 
 * Example: Trying to capture a payment that is already VOIDED.
 *   "Cannot capture payment. Current status: VOIDED. Capture only works on AUTHORIZED payments."
 */
public class InvalidStateTransitionException extends PayflowException {

    public InvalidStateTransitionException(String currentState, String attemptedAction) {
        super(
                "INVALID_STATE_TRANSITION",
                String.format("Cannot %s. Current status: '%s'. This action is not allowed in this state.",
                        attemptedAction, currentState),
                HttpStatus.BAD_REQUEST
        );
    }
}
