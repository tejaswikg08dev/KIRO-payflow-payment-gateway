package com.payflow.routing.iso8583;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines the format of each ISO 8583 field we support.
 * This is our "field specification" — tells encoder/decoder how to handle each field.
 */
public class FieldDefinitions {

    private static final Map<Integer, FieldDefinition> DEFINITIONS = new HashMap<>();

    static {
        // Field 2: PAN (Card Number) — variable length up to 19 digits
        DEFINITIONS.put(2, new FieldDefinition(2, "PAN", FieldType.LLVAR, 19));
        // Field 3: Processing Code — always 6 digits
        DEFINITIONS.put(3, new FieldDefinition(3, "Processing Code", FieldType.FIXED, 6));
        // Field 4: Transaction Amount — always 12 digits (in smallest currency unit)
        DEFINITIONS.put(4, new FieldDefinition(4, "Amount", FieldType.FIXED, 12));
        // Field 7: Transmission Date & Time — MMDDhhmmss
        DEFINITIONS.put(7, new FieldDefinition(7, "Transmission DateTime", FieldType.FIXED, 10));
        // Field 11: STAN — System Trace Audit Number
        DEFINITIONS.put(11, new FieldDefinition(11, "STAN", FieldType.FIXED, 6));
        // Field 12: Local Transaction Time — hhmmss
        DEFINITIONS.put(12, new FieldDefinition(12, "Local Time", FieldType.FIXED, 6));
        // Field 13: Local Transaction Date — MMDD
        DEFINITIONS.put(13, new FieldDefinition(13, "Local Date", FieldType.FIXED, 4));
        // Field 14: Expiration Date — YYMM
        DEFINITIONS.put(14, new FieldDefinition(14, "Expiry Date", FieldType.FIXED, 4));
        // Field 22: POS Entry Mode
        DEFINITIONS.put(22, new FieldDefinition(22, "POS Entry Mode", FieldType.FIXED, 3));
        // Field 25: POS Condition Code
        DEFINITIONS.put(25, new FieldDefinition(25, "POS Condition", FieldType.FIXED, 2));
        // Field 32: Acquiring Institution ID
        DEFINITIONS.put(32, new FieldDefinition(32, "Acquirer ID", FieldType.LLVAR, 11));
        // Field 37: Retrieval Reference Number
        DEFINITIONS.put(37, new FieldDefinition(37, "RRN", FieldType.FIXED, 12));
        // Field 38: Authorization ID Response (auth code)
        DEFINITIONS.put(38, new FieldDefinition(38, "Auth Code", FieldType.FIXED, 6));
        // Field 39: Response Code
        DEFINITIONS.put(39, new FieldDefinition(39, "Response Code", FieldType.FIXED, 2));
        // Field 41: Card Acceptor Terminal ID
        DEFINITIONS.put(41, new FieldDefinition(41, "Terminal ID", FieldType.FIXED, 8));
        // Field 42: Card Acceptor ID (Merchant)
        DEFINITIONS.put(42, new FieldDefinition(42, "Merchant ID", FieldType.FIXED, 15));
        // Field 43: Card Acceptor Name/Location
        DEFINITIONS.put(43, new FieldDefinition(43, "Merchant Name", FieldType.FIXED, 40));
        // Field 49: Currency Code
        DEFINITIONS.put(49, new FieldDefinition(49, "Currency", FieldType.FIXED, 3));
    }

    public static FieldDefinition get(int fieldNumber) {
        return DEFINITIONS.get(fieldNumber);
    }
}
