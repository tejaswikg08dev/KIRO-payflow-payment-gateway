package com.payflow.routing.iso8583;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Decodes binary bytes (received from bank via TCP) into Iso8583Message Java object.
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

        // 1. Read MTI (4 bytes)
        message.setMti(new String(data, offset, 4, StandardCharsets.US_ASCII));
        offset += 4;

        // 2. Read bitmap (16 hex characters)
        String hexBitmap = new String(data, offset, 16, StandardCharsets.US_ASCII);
        BitSet bitmap = hexToBitmap(hexBitmap);
        message.setBitmap(bitmap);
        offset += 16;

        // 3. Read each field indicated by bitmap
        for (int i = 2; i <= 64; i++) {
            if (bitmap.get(i - 1)) { // bitmap is 0-indexed
                FieldDefinition def = FieldDefinitions.get(i);
                if (def == null) {
                    log.warn("Unknown field {} in response, skipping", i);
                    continue;
                }

                String value;
                switch (def.type()) {
                    case FIXED:
                        value = new String(data, offset, def.length(), StandardCharsets.US_ASCII).trim();
                        offset += def.length();
                        break;
                    case LLVAR:
                        int len2 = Integer.parseInt(new String(data, offset, 2, StandardCharsets.US_ASCII));
                        offset += 2;
                        value = new String(data, offset, len2, StandardCharsets.US_ASCII);
                        offset += len2;
                        break;
                    case LLLVAR:
                        int len3 = Integer.parseInt(new String(data, offset, 3, StandardCharsets.US_ASCII));
                        offset += 3;
                        value = new String(data, offset, len3, StandardCharsets.US_ASCII);
                        offset += len3;
                        break;
                    default:
                        throw new IllegalStateException("Unknown field type for field " + i);
                }

                message.setField(i, value);
            }
        }

        log.debug("Decoded ISO 8583: MTI={}, fields={}", message.getMti(), message.getFields().size());
        return message;
    }

    /**
     * Convert 16-char hex string to BitSet.
     */
    private BitSet hexToBitmap(String hex) {
        long value = Long.parseUnsignedLong(hex, 16);
        BitSet bitmap = new BitSet(64);
        for (int i = 0; i < 64; i++) {
            if ((value & (1L << (63 - i))) != 0) {
                bitmap.set(i);
            }
        }
        return bitmap;
    }
}
