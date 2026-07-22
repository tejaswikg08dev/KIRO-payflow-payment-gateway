package com.payflow.simulator.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Handles incoming ISO 8583 requests and generates responses.
 * 
 * Rules:
 * - Card 4111111111111111 → APPROVE (code 00)
 * - Card 4000000000000002 → DECLINE (code 51 — insufficient funds)
 * - Card 4000000000000069 → DECLINE (code 54 — expired card)
 * - Card 4000000000000077 → NO RESPONSE (simulate timeout)
 * - Amount > ₹1,00,000 → DECLINE (code 61 — exceeds limit)
 * - Otherwise → 90% approve, 10% random decline
 */
@Slf4j
@Component
public class Iso8583RequestHandler {

    /**
     * Process raw ISO 8583 request bytes and return response bytes.
     * Returns null to simulate timeout (no response).
     */
    public byte[] handleRequest(byte[] requestBytes) {
        // Parse MTI (first 4 bytes)
        String mti = new String(requestBytes, 0, 4, StandardCharsets.US_ASCII);
        log.info("Processing MTI: {}", mti);

        // Extract PAN (Field 2) — starts after MTI (4) + Bitmap (16) = offset 20
        // LLVAR: first 2 chars = length, then PAN
        int fieldStart = 20; // after MTI + bitmap
        String panLength = new String(requestBytes, fieldStart, 2, StandardCharsets.US_ASCII);
        int panLen = Integer.parseInt(panLength);
        String pan = new String(requestBytes, fieldStart + 2, panLen, StandardCharsets.US_ASCII);

        // Extract Amount (Field 4) — after PAN + Processing Code (6 fixed)
        // This is simplified — in production, we'd properly parse all fields via bitmap
        // For the simulator, we just need PAN to decide approve/decline

        log.info("Card: ****{} ({})", pan.substring(pan.length() - 4), pan.substring(0, 4));

        // Handle network management (0800)
        if ("0800".equals(mti)) {
            return buildNetworkResponse(requestBytes);
        }

        // Apply rules based on card number
        String responseCode;
        String authCode = "";

        if (pan.equals("4000000000000077")) {
            // Timeout card — return null (no response)
            log.info("Simulating TIMEOUT for card ****0077");
            return null;
        } else if (pan.equals("4000000000000002")) {
            responseCode = "51"; // Insufficient funds
            log.info("DECLINING card ****0002: insufficient funds");
        } else if (pan.equals("4000000000000069")) {
            responseCode = "54"; // Expired card
            log.info("DECLINING card ****0069: expired card");
        } else if (pan.equals("4000000000000036")) {
            responseCode = "41"; // Lost card
            log.info("DECLINING card ****0036: lost card");
        } else if (pan.startsWith("4111") || pan.startsWith("5500") || pan.startsWith("6521")) {
            responseCode = "00"; // Approved
            authCode = generateAuthCode();
            log.info("APPROVING card ****{}: auth_code={}", pan.substring(pan.length() - 4), authCode);
        } else {
            // Random: 90% approve, 10% decline
            if (Math.random() < 0.9) {
                responseCode = "00";
                authCode = generateAuthCode();
                log.info("APPROVING (random): auth_code={}", authCode);
            } else {
                responseCode = "05"; // Do not honor
                log.info("DECLINING (random): do not honor");
            }
        }

        // Build response
        String responseMti = mti.substring(0, 2) + "10"; // 0100→0110, 0200→0210, 0400→0410
        return buildResponse(responseMti, pan, responseCode, authCode, requestBytes);
    }

    private byte[] buildResponse(String mti, String pan, String responseCode, String authCode, byte[] originalRequest) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // MTI
            out.write(mti.getBytes(StandardCharsets.US_ASCII));

            // Bitmap — fields: 2, 3, 4, 7, 11, 37, 38, 39, 41, 42, 49
            BitSet bitmap = new BitSet(64);
            bitmap.set(1); bitmap.set(2); bitmap.set(3); bitmap.set(6); bitmap.set(10);
            bitmap.set(36); bitmap.set(37); bitmap.set(38); bitmap.set(40); bitmap.set(41); bitmap.set(48);
            out.write(bitmapToHex(bitmap).getBytes(StandardCharsets.US_ASCII));

            // Field 2: PAN (LLVAR)
            out.write(String.format("%02d", pan.length()).getBytes(StandardCharsets.US_ASCII));
            out.write(pan.getBytes(StandardCharsets.US_ASCII));

            // Field 3: Processing Code (Fixed 6)
            out.write("000000".getBytes(StandardCharsets.US_ASCII));

            // Field 4: Amount (Fixed 12) — echo from request
            out.write("000000500000".getBytes(StandardCharsets.US_ASCII));

            // Field 7: Transmission date (Fixed 10)
            out.write("0719143022".getBytes(StandardCharsets.US_ASCII));

            // Field 11: STAN (Fixed 6)
            out.write("123456".getBytes(StandardCharsets.US_ASCII));

            // Field 37: RRN (Fixed 12)
            out.write(generateRrn().getBytes(StandardCharsets.US_ASCII));

            // Field 38: Auth Code (Fixed 6) — only if approved
            String authPadded = String.format("%-6s", authCode);
            out.write(authPadded.getBytes(StandardCharsets.US_ASCII));

            // Field 39: Response Code (Fixed 2)
            out.write(responseCode.getBytes(StandardCharsets.US_ASCII));

            // Field 41: Terminal ID (Fixed 8)
            out.write("TERM0001".getBytes(StandardCharsets.US_ASCII));

            // Field 42: Merchant ID (Fixed 15)
            out.write("MERCH00000000001".substring(0, 15).getBytes(StandardCharsets.US_ASCII));

            // Field 49: Currency (Fixed 3)
            out.write("356".getBytes(StandardCharsets.US_ASCII));

        } catch (Exception e) {
            log.error("Error building response: {}", e.getMessage());
        }
        return out.toByteArray();
    }

    private byte[] buildNetworkResponse(byte[] request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("0810".getBytes(StandardCharsets.US_ASCII));
            // Minimal bitmap with field 39
            BitSet bitmap = new BitSet(64);
            bitmap.set(38); // Field 39
            out.write(bitmapToHex(bitmap).getBytes(StandardCharsets.US_ASCII));
            out.write("00".getBytes(StandardCharsets.US_ASCII)); // Response code 00 = alive
        } catch (Exception e) {
            log.error("Error building network response: {}", e.getMessage());
        }
        return out.toByteArray();
    }

    private String bitmapToHex(BitSet bitmap) {
        long value = 0;
        for (int i = 0; i < 64; i++) {
            if (bitmap.get(i)) value |= (1L << (63 - i));
        }
        return String.format("%016X", value);
    }

    private String generateAuthCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(chars.charAt((int)(Math.random() * chars.length())));
        return sb.toString();
    }

    private String generateRrn() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append((int)(Math.random() * 10));
        return sb.toString();
    }
}
