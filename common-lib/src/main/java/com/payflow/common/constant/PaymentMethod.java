package com.payflow.common.constant;

/**
 * Payment methods supported by PayFlow.
 */
public enum PaymentMethod {

    /** Credit or Debit card (Visa, Mastercard, RuPay) */
    CARD,

    /** UPI (Unified Payments Interface) — India's instant payment */
    UPI,

    /** Net Banking (redirect to bank website) */
    NETBANKING,

    /** Internal wallet balance */
    WALLET
}
