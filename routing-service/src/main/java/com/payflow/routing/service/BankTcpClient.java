package com.payflow.routing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * TCP Client that communicates with the Bank Simulator.
 * 
 * Protocol:
 * 1. Open TCP connection to bank simulator (localhost:9000)
 * 2. Send: [2-byte message length][ISO 8583 message bytes]
 * 3. Receive: [2-byte response length][ISO 8583 response bytes]
 * 4. Close connection (or keep-alive for pooling)
 * 
 * Timeout: 5 seconds. If bank doesn't respond, return null.
 */
@Slf4j
@Component
public class BankTcpClient {

    @Value("${bank.simulator.host:localhost}")
    private String bankHost;

    @Value("${bank.simulator.port:9000}")
    private int bankPort;

    @Value("${bank.simulator.timeout-ms:5000}")
    private int timeoutMs;

    /**
     * Send ISO 8583 bytes to bank and wait for response.
     * Returns null if timeout or connection error.
     */
    public byte[] sendAndReceive(byte[] requestBytes) {
        try (Socket socket = new Socket(bankHost, bankPort)) {
            socket.setSoTimeout(timeoutMs);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send: [2-byte length][message]
            out.writeShort(requestBytes.length);
            out.write(requestBytes);
            out.flush();

            log.debug("Sent {} bytes to bank at {}:{}", requestBytes.length, bankHost, bankPort);

            // Receive: [2-byte length][response]
            int responseLength = in.readUnsignedShort();
            byte[] responseBytes = new byte[responseLength];
            in.readFully(responseBytes);

            log.debug("Received {} bytes from bank", responseLength);
            return responseBytes;

        } catch (SocketTimeoutException e) {
            log.warn("Bank timeout after {}ms ({}:{})", timeoutMs, bankHost, bankPort);
            return null;
        } catch (IOException e) {
            log.error("Bank communication error: {}", e.getMessage());
            return null;
        }
    }
}
