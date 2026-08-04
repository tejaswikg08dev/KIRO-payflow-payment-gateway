# Hands-On Guide — Phase 7 Part 4: ISO 8583 Message Classes

## Goal

By the end of Part 4, you will have:
- Iso8583Message.java — represents one ISO 8583 message (MTI + bitmap + fields)
- FieldDefinition.java — describes format of each field (type, length)
- FieldDefinitions.java — registry of all fields we support
- FieldType.java — FIXED, LLVAR, LLLVAR enum
- Understanding of how a binary message is structured as Java objects
- Git commit

## Prerequisites

- Part 3 completed (routing engine references these classes)
- Read Phase 1 Part 2 (ISO 8583 protocol theory)

---

## How Java Objects Map to Binary Wire Format

```
JAVA OBJECT:                              BINARY WIRE (sent via TCP):
┌──────────────────────┐                  ┌────────────────────────────────────┐
│ Iso8583Message       │                  │ 0100                               │ ← MTI (4 bytes)
│   mti = "0100"      │    encode()      │ 7238410014C01000                   │ ← Bitmap (16 hex)
│   fields = {        │ ──────────────►  │ 164111111111111111                 │ ← Field 2 (LLVAR)
│     2: "4111..."    │                  │ 000000                             │ ← Field 3 (Fixed 6)
│     3: "000000"     │                  │ 000000500000                       │ ← Field 4 (Fixed 12)
│     4: "00000050.." │                  │ 0719143022                         │ ← Field 7 (Fixed 10)
│     11: "123456"    │                  │ ...more fields...                  │
│     ...             │                  └────────────────────────────────────┘
│   }                  │
└──────────────────────┘                  decode() reverses this (binary → object)
```

---

## Step 4.1: FieldType Enum

**Create file:** `routing-service/src/main/java/com/payflow/routing/iso8583/FieldType.java`

```java
package com.payflow.routing.iso8583;

/**
 * ISO 8583 field encoding types.
 * 
 * FIXED:  Field is always exactly N characters.
 *         No length prefix needed.
 *         Padded with zeros (numeric) or spaces (alpha) if shorter.
 *         Example: Field 4 (Amount) = always 12 digits → "000000500000"
 * 
 * LLVAR:  Variable length, 2-digit length prefix.
 *         First 2 characters tell how long the data is.
 *         Max 99 characters.
 *         Example: Field 2 (PAN) = "16" + "4111111111111111" (16 digits)
 * 
 * LLLVAR: Variable length, 3-digit length prefix.
 *         First 3 characters tell how long the data is.
 *         Max 999 characters.
 *         Example: Field 55 (EMV) = "032" + 32 bytes of chip data
 */
public enum FieldType {
    FIXED,   // Fixed length (padded)
    LLVAR,   // 2-digit length prefix + variable data
    LLLVAR   // 3-digit length prefix + variable data
}
```

---

## Step 4.2: FieldDefinition Record

**Create file:** `routing-service/src/main/java/com/payflow/routing/iso8583/FieldDefinition.java`

```java
package com.payflow.routing.iso8583;

/**
 * Definition of a single ISO 8583 field.
 * 
 * Tells the encoder/decoder:
 * - Which field number (2, 3, 4, ..., 64)
 * - Human-readable name (for logging)
 * - Type: FIXED, LLVAR, or LLLVAR
 * - Max length (exact length for FIXED)
 * 
 * Java Record: immutable data class — constructor, getters, equals, hashCode auto-generated.
 * Same as a class with final fields but much less code.
 */
public record FieldDefinition(
    int number,      // Field number: 2, 3, 4, ..., 64
    String name,     // Human name: "PAN", "Amount", "Auth Code"
    FieldType type,  // FIXED, LLVAR, or LLLVAR
    int length       // Max length (or exact length for FIXED)
) {
    // Example: new FieldDefinition(2, "PAN", FieldType.LLVAR, 19)
    // Means: Field 2 is named "PAN", variable length (LLVAR), max 19 chars
}
```

---

## Step 4.3: FieldDefinitions Registry

**Create file:** `routing-service/src/main/java/com/payflow/routing/iso8583/FieldDefinitions.java`

```java
package com.payflow.routing.iso8583;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of ALL ISO 8583 fields we support.
 * 
 * This is our "data dictionary" — tells encoder/decoder how to handle each field.
 * Real payment systems support 128+ fields. We implement the 18 most important ones.
 */
public class FieldDefinitions {

    private static final Map<Integer, FieldDefinition> DEFINITIONS = new HashMap<>();

    static {
        //                                                    Type      Length
        // Card & Transaction Identity
        DEFINITIONS.put(2,  new FieldDefinition(2,  "PAN",              FieldType.LLVAR,  19));
        DEFINITIONS.put(3,  new FieldDefinition(3,  "Processing Code",  FieldType.FIXED,   6));
        DEFINITIONS.put(4,  new FieldDefinition(4,  "Amount",           FieldType.FIXED,  12));

        // Date & Time
        DEFINITIONS.put(7,  new FieldDefinition(7,  "Transmission DateTime", FieldType.FIXED, 10));
        DEFINITIONS.put(11, new FieldDefinition(11, "STAN",             FieldType.FIXED,   6));
        DEFINITIONS.put(12, new FieldDefinition(12, "Local Time",       FieldType.FIXED,   6));
        DEFINITIONS.put(13, new FieldDefinition(13, "Local Date",       FieldType.FIXED,   4));
        DEFINITIONS.put(14, new FieldDefinition(14, "Expiry Date",      FieldType.FIXED,   4));

        // Terminal & POS
        DEFINITIONS.put(22, new FieldDefinition(22, "POS Entry Mode",   FieldType.FIXED,   3));
        DEFINITIONS.put(25, new FieldDefinition(25, "POS Condition",    FieldType.FIXED,   2));

        // Institution
        DEFINITIONS.put(32, new FieldDefinition(32, "Acquirer ID",      FieldType.LLVAR,  11));

        // Response Fields (in bank's response)
        DEFINITIONS.put(37, new FieldDefinition(37, "RRN",              FieldType.FIXED,  12));
        DEFINITIONS.put(38, new FieldDefinition(38, "Auth Code",        FieldType.FIXED,   6));
        DEFINITIONS.put(39, new FieldDefinition(39, "Response Code",    FieldType.FIXED,   2));

        // Merchant/Terminal
        DEFINITIONS.put(41, new FieldDefinition(41, "Terminal ID",      FieldType.FIXED,   8));
        DEFINITIONS.put(42, new FieldDefinition(42, "Merchant ID",      FieldType.FIXED,  15));
        DEFINITIONS.put(43, new FieldDefinition(43, "Merchant Name",    FieldType.FIXED,  40));

        // Currency
        DEFINITIONS.put(49, new FieldDefinition(49, "Currency",         FieldType.FIXED,   3));
    }

    /**
     * Get field definition by field number.
     * Returns null if field is not in our supported set.
     */
    public static FieldDefinition get(int fieldNumber) {
        return DEFINITIONS.get(fieldNumber);
    }
}
```

---

## Step 4.4: Iso8583Message Class

**Create file:** `routing-service/src/main/java/com/payflow/routing/iso8583/Iso8583Message.java`

```java
package com.payflow.routing.iso8583;

import lombok.Data;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single ISO 8583 financial message.
 * 
 * Three components:
 * 1. MTI (Message Type Indicator): "0100", "0110", "0400", etc.
 * 2. Bitmap: 64 bits indicating which fields are present
 * 3. Fields: Map of field number → field value
 * 
 * Usage:
 *   // Build an authorization request
 *   Iso8583Message msg = new Iso8583Message("0100");
 *   msg.setField(2, "4111111111111111");  // Card number
 *   msg.setField(4, "000000500000");       // Amount
 *   msg.setField(49, "356");               // Currency INR
 *   
 *   // Encode and send
 *   byte[] bytes = encoder.encode(msg);
 *   bankClient.send(bytes);
 *   
 *   // Receive and decode response
 *   byte[] responseBytes = bankClient.receive();
 *   Iso8583Message response = decoder.decode(responseBytes);
 *   String authCode = response.getField(38);  // "A1B2C3"
 *   String respCode = response.getField(39);  // "00" = approved
 */
@Data
public class Iso8583Message {

    private String mti;
    // Message Type Indicator: 4 characters
    // "0100" = Authorization Request
    // "0110" = Authorization Response
    // "0400" = Reversal Request
    // "0810" = Network Management Response

    private BitSet bitmap;
    // 64 bits: bit N is set if field N is present
    // Java BitSet: dynamic size, provides set()/get()/clear()

    private Map<Integer, String> fields;
    // Field number (2-64) → field value (as String)
    // All values stored as strings (numeric fields are numeric strings)

    public Iso8583Message() {
        this.bitmap = new BitSet(64);
        this.fields = new HashMap<>();
    }

    public Iso8583Message(String mti) {
        this();
        this.mti = mti;
    }

    /**
     * Set a field value. Automatically marks it as present in bitmap.
     */
    public void setField(int fieldNumber, String value) {
        if (fieldNumber < 2 || fieldNumber > 64) {
            throw new IllegalArgumentException(
                "Field number must be 2-64, got: " + fieldNumber);
        }
        fields.put(fieldNumber, value);
        bitmap.set(fieldNumber - 1);
        // Bitmap is 0-indexed internally (bit 0 = field 1, bit 1 = field 2, ...)
    }

    /** Get field value (null if not present) */
    public String getField(int fieldNumber) {
        return fields.get(fieldNumber);
    }

    /** Check if field exists in this message */
    public boolean hasField(int fieldNumber) {
        return fields.containsKey(fieldNumber);
    }

    /** Convenience: Get response code (Field 39) */
    public String getResponseCode() { return getField(39); }

    /** Convenience: Get auth code (Field 38) */
    public String getAuthCode() { return getField(38); }

    /** Convenience: Get RRN (Field 37) */
    public String getRrn() { return getField(37); }

    /** Convenience: Is this an approved response? */
    public boolean isApproved() { return "00".equals(getResponseCode()); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ISO8583[MTI=" + mti);
        fields.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append(", F").append(e.getKey()).append("=").append(e.getValue()));
        sb.append("]");
        return sb.toString();
    }
}
```

---

## Step 4.5: Git Commit

```cmd
git add routing-service/src/main/java/com/payflow/routing/iso8583/
git commit -m "Phase 7 Part 4: ISO 8583 message classes - Iso8583Message, FieldDefinitions, FieldType"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `FieldType.java` | Enum: FIXED, LLVAR, LLLVAR |
| `FieldDefinition.java` | Record: describes one field (number, name, type, length) |
| `FieldDefinitions.java` | Registry: maps field number → definition (18 fields) |
| `Iso8583Message.java` | The message itself: MTI + bitmap + fields map |

These 4 classes define the DATA STRUCTURE. Parts 5-6 create the encoder (Java → bytes) and decoder (bytes → Java).

---

## Interview Notes

**Q: "What is a bitmap in ISO 8583?"**
> "A 64-bit binary number where each bit represents whether a specific data field is present in the message. Bit 2 = 1 means Field 2 (card number) is included. The receiver parses only the fields indicated by the bitmap, skipping absent fields. This makes the format compact — you only transmit what's needed."

**Q: "Why use a Map for fields instead of fixed object fields?"**
> "ISO 8583 has 128 possible fields but any given message uses only 10-20. A Map is sparse and flexible — we only store what's present. Also, different message types use different field combinations, so a fixed class structure wouldn't work well."

---

## Next Step

→ Continue to **Phase 7 Part 5: ISO 8583 Encoder & Decoder**
