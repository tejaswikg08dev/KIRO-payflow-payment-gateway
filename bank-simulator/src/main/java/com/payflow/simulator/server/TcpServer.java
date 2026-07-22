package com.payflow.simulator.server;

import com.payflow.simulator.handler.Iso8583RequestHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP Server that listens for ISO 8583 messages on port 9000.
 * 
 * Protocol (per connection):
 * 1. Client connects
 * 2. Client sends: [2-byte length][ISO 8583 bytes]
 * 3. Server processes and responds: [2-byte length][ISO 8583 response bytes]
 * 4. Connection closes (or stays open for more messages)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcpServer {

    private final Iso8583RequestHandler requestHandler;

    @Value("${bank.simulator.port:9000}")
    private int port;

    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("╔══════════════════════════════════════════╗");
                log.info("║   Bank Simulator started on port {}    ║", port);
                log.info("║   Waiting for ISO 8583 messages...      ║");
                log.info("╚══════════════════════════════════════════╝");

                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleConnection(clientSocket));
                }
            } catch (IOException e) {
                log.error("Bank simulator TCP server error: {}", e.getMessage());
            }
        }, "bank-simulator-tcp").start();
    }

    private void handleConnection(Socket socket) {
        try (socket;
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Read message length (2 bytes)
            int messageLength = in.readUnsignedShort();

            // Read message bytes
            byte[] requestBytes = new byte[messageLength];
            in.readFully(requestBytes);

            log.info("Received {} bytes from routing-service", messageLength);

            // Process and generate response
            byte[] responseBytes = requestHandler.handleRequest(requestBytes);

            if (responseBytes != null) {
                // Simulate bank processing delay (100-300ms)
                Thread.sleep(100 + (long)(Math.random() * 200));

                // Send response: [2-byte length][response bytes]
                out.writeShort(responseBytes.length);
                out.write(responseBytes);
                out.flush();

                log.info("Sent {} bytes response", responseBytes.length);
            }
            // If responseBytes is null, we don't respond (simulates timeout)

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.warn("Connection handling error: {}", e.getMessage());
        }
    }
}
