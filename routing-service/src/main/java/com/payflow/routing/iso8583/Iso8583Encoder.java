package com.payflow.routing.iso8583;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Encodes an Iso8583Message Java object into binary bytes for TCP transmission.
 * 
 * Wire format:
 * [4 bytes MTI][16 bytes hex bitmap][variable data fields in order]
 * 
 * Field encoding:
 * - Fixed length: padded to exact length (zeros for numeric, spaces for alpha)
 * - LLVAR: 2-digit length prefix + data
 * - LLLVAR: 3-digit length prefix + data
 */
@Slf4j
@Component
public class Iso8583Encoder {

    /**
     * Encode message to bytes ready for TCP transmission.
     */
    public byte[] encode(Iso8583Message message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            // 1. Write MTI (4 ASCII bytes)
            out.write(message.getMti().getBytes(StandardCharsets.US_ASCII));

            // 2. Calculate and write bitmap (16 hex characters = 8 bytes)
            String hexBitmap = bitmapToHex(message.getBitmap());
            out.write(hexBitmap.getBytes(StandardCharsets.US_ASCII));

            // 3. Write each data field in order (field 2 to 64)
            for (int i = 2; i <= 64; i++) {
                if (message.hasField(i)) {
                    byte[] fieldBytes = encodeField(i, message.getField(i));
                    out.write(fieldBytes);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode ISO 8583 message", e);
        }

        byte[] result = out.toByteArray();
        log.debug("Encoded ISO 8583: {} bytes, MTI={}", result.length, message.getMti());
        return result;
    }

    /**
     * Encode a single field based on its definition.
     */
    private byte[] encodeField(int fieldNumber, String value) {
        FieldDefinition def = FieldDefinitions.get(fieldNumber);
        if (def == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldNumber);
        }

        return switch (def.type()) {
            case FIXED -> encodeFixed(value, def.length());
            case LLVAR -> encodeLLVAR(value);
            case LLLVAR -> encodeLLLVAR(value);
        };
    }

    private byte[] encodeFixed(String value, int length) {
        // Pad to exact length: right-pad with spaces for alpha, left-pad with zeros for numeric
        String padded = String.format("%-" + length + "s", value).substring(0, length);
        return padded.getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] encodeLLVAR(String value) {
        String prefix = String.format("%02d", value.length());
        return (prefix + value).getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] encodeLLLVAR(String value) {
        String prefix = String.format("%03d", value.length());
        return (prefix + value).getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Convert BitSet to 16-character hex string.
     */
    private String bitmapToHex(BitSet bitmap) {
        long value = 0;
        for (int i = 0; i < 64; i++) {
            if (bitmap.get(i)) {
                value |= (1L << (63 - i));
            }
        }
        return String.format("%016X", value);
    }
}
