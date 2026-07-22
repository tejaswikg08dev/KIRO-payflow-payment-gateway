package com.payflow.routing.service;

import com.payflow.routing.dto.RouteRequest;
import com.payflow.routing.dto.RouteResponse;
import com.payflow.routing.iso8583.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Routing Engine — The brain that decides which bank to route to,
 * builds the ISO 8583 message, and communicates with the bank simulator.
 * 
 * In the real world, this would:
 * 1. Score multiple bank routes (HDFC, ICICI, Axis)
 * 2. Pick the best one (highest success rate, lowest cost)
 * 3. Send ISO 8583 via TCP to that bank
 * 4. If it fails, try the next best bank (failover)
 * 
 * For now, we route to our single bank simulator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingEngine {

    private final Iso8583Encoder encoder;
    private final Iso8583Decoder decoder;
    private final BankTcpClient bankClient;

    /**
     * Route a payment — build ISO 8583, send to bank, return result.
     */
    public RouteResponse routePayment(RouteRequest request) {
        log.info("Routing payment: amount={}, card=****{}", request.getAmount(), request.getCardLast4());

        // 1. Select route (simplified — always use HDFC_ACQ_01)
        String selectedRoute = selectBestRoute(request);

        // 2. Build ISO 8583 Authorization Request (0100)
        Iso8583Message authRequest = buildAuthorizationRequest(request);
        log.debug("Built ISO 8583 message: {}", authRequest);

        // 3. Encode to bytes
        byte[] requestBytes = encoder.encode(authRequest);

        // 4. Send to bank simulator via TCP and get response
        byte[] responseBytes = bankClient.sendAndReceive(requestBytes);

        if (responseBytes == null) {
            // Bank didn't respond (timeout)
            log.warn("Bank timeout for payment. Sending reversal.");
            return RouteResponse.builder()
                    .success(false)
                    .routeUsed(selectedRoute)
                    .responseCode("91")
                    .failureReason("Bank timeout — no response within 5 seconds")
                    .build();
        }

        // 5. Decode bank's response
        Iso8583Message authResponse = decoder.decode(responseBytes);
        log.info("Bank response: MTI={}, code={}", authResponse.getMti(), authResponse.getResponseCode());

        // 6. Build our response
        return RouteResponse.builder()
                .success(authResponse.isApproved())
                .routeUsed(selectedRoute)
                .responseCode(authResponse.getResponseCode())
                .authCode(authResponse.getAuthCode())
                .rrn(authResponse.getRrn())
                .failureReason(authResponse.isApproved() ? null : "Bank declined: code " + authResponse.getResponseCode())
                .build();
    }

    /**
     * Select best route. In production, this uses the multi-armed bandit algorithm.
     * For now, always returns the simulated HDFC acquirer.
     */
    private String selectBestRoute(RouteRequest request) {
        // TODO: Implement multi-armed bandit scoring
        // For now: single route
        return "HDFC_ACQ_01";
    }

    /**
     * Build ISO 8583 0100 Authorization Request message.
     */
    private Iso8583Message buildAuthorizationRequest(RouteRequest request) {
        Iso8583Message msg = new Iso8583Message("0100");

        LocalDateTime now = LocalDateTime.now();

        msg.setField(2, request.getCardNumber());                          // PAN
        msg.setField(3, "000000");                                          // Processing code: purchase
        msg.setField(4, formatAmount(request.getAmount()));                 // Amount in paise (12 digits)
        msg.setField(7, now.format(DateTimeFormatter.ofPattern("MMddHHmmss"))); // Transmission date/time
        msg.setField(11, generateStan());                                   // STAN
        msg.setField(12, now.format(DateTimeFormatter.ofPattern("HHmmss"))); // Local time
        msg.setField(13, now.format(DateTimeFormatter.ofPattern("MMdd")));   // Local date
        msg.setField(14, request.getCardExpiry());                          // Card expiry YYMM
        msg.setField(22, "081");                                            // E-commerce
        msg.setField(25, "00");                                             // Normal transaction
        msg.setField(32, "12345678");                                       // Our acquirer ID
        msg.setField(41, "TERM0001");                                       // Terminal ID
        msg.setField(42, padRight(request.getMerchantId(), 15));            // Merchant ID
        msg.setField(43, padRight("PayFlow Merchant Mumbai IN", 40));       // Merchant name
        msg.setField(49, "356");                                            // INR

        return msg;
    }

    private String formatAmount(long amountInPaise) {
        return String.format("%012d", amountInPaise);
    }

    private String generateStan() {
        return String.format("%06d", (int) (Math.random() * 999999));
    }

    private String padRight(String str, int length) {
        return String.format("%-" + length + "s", str).substring(0, length);
    }
}
