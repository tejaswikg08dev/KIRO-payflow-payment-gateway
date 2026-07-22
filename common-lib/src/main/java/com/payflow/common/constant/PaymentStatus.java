package com.payflow.common.constant;

/**
 * All possible payment states in the PayFlow system.
 * 
 * State machine transitions:
 * CREATED → PROCESSING → AUTHORIZED → CAPTURED → SETTLED
 *                       → FAILED
 *            AUTHORIZED → VOIDED
 *            AUTHORIZED → EXPIRED
 *            CAPTURED   → REFUNDED (full)
 * CREATED → EXPIRED (30 min timeout)
 */
public enum PaymentStatus {

    /** Order created, waiting for customer to submit payment details */
    CREATED,

    /** Payment submitted, talking to bank (customer is waiting) */
    PROCESSING,

    /** Bank approved, money is HELD on customer's card (not yet deducted) */
    AUTHORIZED,

    /** Merchant confirmed, money is DEDUCTED from customer */
    CAPTURED,

    /** Money transferred to merchant's bank account (end of day batch) */
    SETTLED,

    /** Merchant cancelled before capture (hold released) */
    VOIDED,

    /** Money returned to customer after capture */
    REFUNDED,

    /** Bank declined or error occurred */
    FAILED,

    /** Customer didn't complete in time (30 min for order, 7 days for auth) */
    EXPIRED
}
