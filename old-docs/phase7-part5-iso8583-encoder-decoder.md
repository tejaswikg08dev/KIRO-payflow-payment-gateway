# Hands-On Guide — Phase 7 Part 5: ISO 8583 Encoder & Decoder

## Goal

By the end of Part 5, you will have:
- Iso8583Encoder: converts Java Iso8583Message → binary byte[] for TCP
- Iso8583Decoder: converts binary byte[] from TCP → Java Iso8583Message
- Understanding of bitmap calculation (which bits to set)
- Understanding of field packing (FIXED padding, LLVAR length prefix)
- Git commit

## Prerequisites

- Part 4 completed (Iso8583Message, FieldDefinitions, FieldType exist)

---

## Encoding Process (Java Object → Binary Bytes)

```
INPUT: Iso8583Message with mti="0100", fields={2:"4111...", 3:"000000", 4:"000000500000", ...}

STEP 1: Write MTI as 4 ASCII bytes
         "0100" → [0x30, 0x31, 0x30, 0x30]

STEP 2: Calculate bitmap from present fields
         Fields 2,3,4,7,11,12,13,14,22,25,32,41,42,43,49 are present
         Set those bits in a 64-bit number
         Convert to 16 hex characters: "7238410014C01000"

STEP 3: Write each field IN ORDER (2, 3, 4, 7, ..., 49)
         For FIXED fields: pad to exact length
           Field 3 = "000000" (already 6 chars) → write as-is
           Field 4 = "000000500000" (already 12 chars) → write as-is
         For LLVAR fields: prepend 2-digit length
           Field 2 = "4111111111111111" (16 chars) → write "16" + "4111111111111111"

OUTPUT: byte[] ready to send over TCP
```

---

## Step 5.1: Iso8583Encoder (Full Code with Comments)

**Create file:** `routing-service/src/main/java/com/payflow/routing/iso8583/Iso8583Encoder.java`

```java
package com.payflow.routing.iso8583;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Encodes Iso8583Message → byte[] for TCP transmission to bank.
 * 
 * Wire format: [4-byte MTI][16-byte hex bitmap][field data in order]
 */
@Slf4j
@Component
public class Iso8583Encoder {

    /**
     * Encode a complete ISO 8583 message to bytes.
     */
    public byte[] encode(Iso8583Message message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // ByteArrayOutputStream: builds a byte[] incrementally (like StringBuilder for bytes)

        try {
            // ===== 1. Write MTI (4 ASCII bytes) =====
            out.write(message.getMti().getBytes(StandardCharsets.US_ASCII));
            // "0100" → [0x30, 0x31, 0x30, 0x30] (ASCII codes for '0','1','0','0')

            // ===== 2. Calculate and write bitmap (16 hex characters) =====
            String hexBitmap = bitmapToHex(message.getBitmap());
            out.write(hexBitmap.getBytes(StandardCharsets.US_ASCII));
            // 16 hex chars like "7238410014C01000"

            // ===== 3. Write each data field in order (field 2 through 64) =====
            for (int fieldNum = 2; fieldNum <= 64; fieldNum++) {
                if (message.hasField(fieldNum)) {
                    byte[] fieldBytes = encodeField(fieldNum, message.getField(fieldNum));
                    out.write(fieldBytes);
                }
            }
            // Fields MUST be written in ascending order (ISO 8583 rule)
            // Decoder reads them in same order, guided by bitmap

        } catch (Exception e) {
            throw new RuntimeException("Failed to encode ISO 8583 message: " + e.getMessage(), e);
        }

        byte[] result = out.toByteArray();
        log.debug("Encoded ISO 8583: {} bytes, MTI={}", result.length, message.getMti());
        return result;
    }

    /**
     * Encode a single field based on its FieldDefinition.
     */
    private byte[] encodeField(int fieldNumber, String value) {
        FieldDefinition def = FieldDefinitions.get(fieldNumber);
        if (def == null) {
            throw new IllegalArgumentException("No definition for field " + fieldNumber);
        }

        return switch (def.type()) {
            case FIXED -> encodeFixed(value, def.length());
            case LLVAR -> encodeLLVAR(value);
            case LLLVAR -> encodeLLLVAR(value);
        };
    }

    /**
     * Encode FIXED-length field: pad to exact length.
     * Numeric fields: left-pad with '0'
     * Alpha fields: right-pad with ' ' (space)
     * 
     * Examples:
     *   "123456" with length 6 → "123456" (already exact)
     *   "A1B2C3" with length 6 → "A1B2C3" (already exact)
     *   "TERM" with length 8 → "TERM    " (right-padded with spaces)
     */
    private byte[] encodeFixed(String value, int length) {
        String padded = String.format("%-" + length + "s", value).substring(0, length);
        // %-8s = left-justify in 8-char field, pad right with spaces
        // .substring(0, length) = truncate if somehow longer
        return padded.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Encode LLVAR field: 2-digit length prefix + data.
     * 
     * Example: "4111111111111111" (16 chars)
     * Encoded: "16" + "4111111111111111" = "164111111111111111"
     *           ↑ length prefix
     */
    private byte[] encodeLLVAR(String value) {
        String prefix = String.format("%02d", value.length());
        // %02d = 2 digits, zero-padded: 16 → "16", 8 → "08"
        return (prefix + value).getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Encode LLLVAR field: 3-digit length prefix + data.
     * Same as LLVAR but 3-digit prefix (supports up to 999 chars).
     */
    private byte[] encodeLLLVAR(String value) {
        String prefix = String.format("%03d", value.length());
        return (prefix + value).getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Convert Java BitSet to 16-character hex string.
     * 
     * How: BitSet has bits set at positions corresponding to present fields.
     * We convert these 64 bits into a long integer, then format as 16 hex chars.
     * 
     * Example: Fields 2,3,4 present → bits 1,2,3 set → binary: 0111... → hex: "7..."
     */
    private String bitmapToHex(BitSet bitmap) {
        long value = 0;
        for (int i = 0; i < 64; i++) {
            if (bitmap.get(i)) {
                value |= (1L << (63 - i));
                // Set bit at position (63-i) in the long
                // Bit 0 of bitmap → bit 63 of long (most significant)
                // This gives the correct left-to-right bit ordering
            }
        }
        return String.format("%016X", value);
        // Format as 16 uppercase hex characters, zero-padded
        // Result: "7238410014C01000"
    }
}
```

---

## Step 5.2: Iso8583Decoder (Full Code)

**Create file:** `routing-service/src/main/java/com/payflow/routing/iso8583/Iso8583Decoder.java`

```java
package com.payflow.routing.iso8583;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Decodes byte[] received from bank → Iso8583Message Java object.
 * Reverses the encoding process: reads MTI, parses bitmap, extracts fields.
 */
@Slf4j
@Component
public class Iso8583Decoder {

    /**
     * Decode raw bytes into an Iso8583Message.
     */
    public Iso8583Message decode(byte[] data) {
        Iso8583Message message = new Iso8583Message();
        int offset = 0;
        // offset tracks our current position in the byte array

        // ===== 1. Read MTI (first 4 bytes) =====
        message.setMti(new String(data, offset, 4, StandardCharsets.US_ASCII));
        offset += 4;
        // "0110" → authorization response

        // ===== 2. Read Bitmap (next 16 hex characters) =====
        String hexBitmap = new String(data, offset, 16, StandardCharsets.US_ASCII);
        BitSet bitmap = hexToBitmap(hexBitmap);
        message.setBitmap(bitmap);
        offset += 16;

        // ===== 3. Read each field indicated by bitmap =====
        for (int fieldNum = 2; fieldNum <= 64; fieldNum++) {
            if (bitmap.get(fieldNum - 1)) {
                // This field is present (bit is set)
                FieldDefinition def = FieldDefinitions.get(fieldNum);
                if (def == null) {
                    log.warn("Unknown field {} in message, cannot parse further", fieldNum);
                    break; // Can't continue if we don't know field's format
                }

                String value;
                switch (def.type()) {
                    case FIXED:
                        // Read exactly N bytes
                        value = new String(data, offset, def.length(), StandardCharsets.US_ASCII).trim();
                        offset += def.length();
                        break;

                    case LLVAR:
                        // Read 2-byte length prefix, then that many bytes of data
                        int len2 = Integer.parseInt(
                            new String(data, offset, 2, StandardCharsets.US_ASCII));
                        offset += 2;
                        value = new String(data, offset, len2, StandardCharsets.US_ASCII);
                        offset += len2;
                        break;

                    case LLLVAR:
                        // Read 3-byte length prefix, then that many bytes of data
                        int len3 = Integer.parseInt(
                            new String(data, offset, 3, StandardCharsets.US_ASCII));
                        offset += 3;
                        value = new String(data, offset, len3, StandardCharsets.US_ASCII);
                        offset += len3;
                        break;

                    default:
                        throw new IllegalStateException("Unknown field type for field " + fieldNum);
                }

                message.setField(fieldNum, value);
            }
        }

        log.debug("Decoded ISO 8583: MTI={}, {} fields parsed",
                message.getMti(), message.getFields().size());
        return message;
    }

    /**
     * Convert 16-character hex string back to BitSet.
     * Reverse of bitmapToHex in Encoder.
     */
    private BitSet hexToBitmap(String hex) {
        long value = Long.parseUnsignedLong(hex, 16);
        // Parse hex string to long: "7238410014C01000" → 0x7238410014C01000
        
        BitSet bitmap = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((value & (1L << (63 - i))) != 0) {
                bitmap.set(i);
                // If bit at position (63-i) is set in the long,
                // set bit i in our BitSet
            }
        }
        return bitmap;
    }
}
```

---

## Step 5.3: How Encoder + Decoder Work Together

```java
// BUILD a message
Iso8583Message request = new Iso8583Message("0100");
request.setField(2, "4111111111111111");
request.setField(4, "000000500000");
request.setField(49, "356");

// ENCODE to bytes
byte[] bytes = encoder.encode(request);
// bytes = [0x30,0x31,0x30,0x30, 0x34,0x30,0x30,... ] (binary data)

// SEND to bank via TCP...
// RECEIVE response bytes from bank...

// DECODE response
Iso8583Message response = decoder.decode(responseBytes);
// response.getMti() → "0110"
// response.getField(38) → "A1B2C3" (auth code)
// response.getField(39) → "00" (approved)
// response.isApproved() → true
```

---

## Step 5.4: Git Commit

```cmd
git add routing-service/src/main/java/com/payflow/routing/iso8583/Iso8583Encoder.java
git add routing-service/src/main/java/com/payflow/routing/iso8583/Iso8583Decoder.java
git commit -m "Phase 7 Part 5: ISO 8583 Encoder (Java→bytes) and Decoder (bytes→Java)"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `Iso8583Encoder.java` | Converts Iso8583Message → byte[] (MTI + bitmap + field data) |
| `Iso8583Decoder.java` | Converts byte[] → Iso8583Message (parse MTI, bitmap, fields) |

Combined with Part 4's classes, we now have the complete ISO 8583 layer:
- **Data model**: Iso8583Message, FieldDefinition, FieldType, FieldDefinitions
- **Serialization**: Iso8583Encoder (write), Iso8583Decoder (read)

Next: the TCP client that sends/receives these bytes over the network.

---

## Next Step

→ Continue to **Phase 7 Part 6: TCP Client (Bank Communication)**
