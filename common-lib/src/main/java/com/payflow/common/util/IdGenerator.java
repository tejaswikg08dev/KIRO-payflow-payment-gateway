package com.payflow.common.util;

import java.security.SecureRandom;

/**
 * Generates short, URL-friendly, unique IDs for all PayFlow entities.
 * 
 * Format: {prefix}_{10-character-alphanumeric}
 * 
 * Examples:
 *   pay_Hk7mN3xQp2   (payment)
 *   ord_LkR3d9xF2m   (order)
 *   rfnd_Qm4nP8wXv3  (refund)
 *   merch_xyz789abc   (merchant)
 *   key_a1b2c3d4e5    (API key)
 *   evt_f6g7h8i9j0    (event)
 *   stl_Mn2kP9wQr5    (settlement)
 * 
 * Why not UUID?
 * - UUIDs are 36 characters (with dashes) — too long for URLs and display
 * - Our IDs are 14-16 characters — short, readable, still unique enough
 * - Prefix tells you the entity type at a glance
 * 
 * Collision probability:
 * - 10 chars from 62-char alphabet = 62^10 = 839 trillion possibilities
 * - Practically zero collision risk for our scale
 */
public class IdGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ID_LENGTH = 10;

    /**
     * Generate a random ID with given prefix.
     * Example: generateId("pay") → "pay_Hk7mN3xQp2"
     */
    public static String generateId(String prefix) {
        StringBuilder sb = new StringBuilder(prefix.length() + 1 + ID_LENGTH);
        sb.append(prefix).append('_');
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Generate payment ID: pay_xxxxxxxxxx */
    public static String paymentId() {
        return generateId("pay");
    }

    /** Generate order ID: ord_xxxxxxxxxx */
    public static String orderId() {
        return generateId("ord");
    }

    /** Generate refund ID: rfnd_xxxxxxxxxx */
    public static String refundId() {
        return generateId("rfnd");
    }

    /** Generate merchant ID: merch_xxxxxxxxxx */
    public static String merchantId() {
        return generateId("merch");
    }

    /** Generate API key ID: key_xxxxxxxxxx */
    public static String apiKeyId() {
        return generateId("key");
    }

    /** Generate event ID: evt_xxxxxxxxxx */
    public static String eventId() {
        return generateId("evt");
    }

    /** Generate settlement ID: stl_xxxxxxxxxx */
    public static String settlementId() {
        return generateId("stl");
    }

    /** Generate user ID: usr_xxxxxxxxxx */
    public static String userId() {
        return generateId("usr");
    }
}
