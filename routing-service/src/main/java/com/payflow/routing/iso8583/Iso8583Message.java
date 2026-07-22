package com.payflow.routing.iso8583;

import lombok.Data;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single ISO 8583 financial message.
 * 
 * Structure:
 * ┌──────┐ ┌────────┐ ┌──────────────┐
 * │ MTI  │ │ BITMAP │ │ DATA FIELDS  │
 * │4 byte│ │16 byte │ │ variable     │
 * └──────┘ └────────┘ └──────────────┘
 * 
 * MTI examples: 0100 (auth request), 0110 (auth response), 0400 (reversal)
 * Bitmap: 64 bits telling which fields are present
 * Data: The actual transaction fields (card number, amount, etc.)
 */
@Data
public class Iso8583Message {

    /** Message Type Indicator: 0100, 0110, 0200, 0210, 0400, 0410, 0800, 0810 */
    private String mti;

    /** Bitmap indicating which fields are present (64 bits) */
    private BitSet bitmap;

    /** Data fields: field number (2-64) → field value */
    private Map<Integer, String> fields;

    public Iso8583Message() {
        this.bitmap = new BitSet(64);
        this.fields = new HashMap<>();
    }

    public Iso8583Message(String mti) {
        this();
        this.mti = mti;
    }

    /**
     * Set a field value and mark it as present in the bitmap.
     */
    public void setField(int fieldNumber, String value) {
        if (fieldNumber < 2 || fieldNumber > 64) {
            throw new IllegalArgumentException("Field number must be between 2 and 64");
        }
        fields.put(fieldNumber, value);
        bitmap.set(fieldNumber - 1); // bitmap is 0-indexed
    }

    /**
     * Get a field value (null if not present).
     */
    public String getField(int fieldNumber) {
        return fields.get(fieldNumber);
    }

    /**
     * Check if a field is present in this message.
     */
    public boolean hasField(int fieldNumber) {
        return fields.containsKey(fieldNumber);
    }

    /**
     * Get response code (Field 39) — "00" = approved.
     */
    public String getResponseCode() {
        return getField(39);
    }

    /**
     * Get authorization code (Field 38).
     */
    public String getAuthCode() {
        return getField(38);
    }

    /**
     * Get RRN (Field 37).
     */
    public String getRrn() {
        return getField(37);
    }

    /**
     * Check if this is an approved response.
     */
    public boolean isApproved() {
        return "00".equals(getResponseCode());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ISO8583[MTI=").append(mti);
        fields.forEach((k, v) -> sb.append(", F").append(k).append("=").append(v));
        sb.append("]");
        return sb.toString();
    }
}
