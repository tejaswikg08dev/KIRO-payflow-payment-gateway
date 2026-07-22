package com.payflow.simulator;

import com.payflow.simulator.server.TcpServer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bank Simulator — Acts as Visa/Mastercard network + Issuing bank.
 * 
 * Starts a TCP server on port 9000 that:
 * 1. Receives ISO 8583 messages from our routing-service
 * 2. Parses the message (reads card number, amount, etc.)
 * 3. Applies rules (approve/decline based on card number and amount)
 * 4. Builds ISO 8583 response
 * 5. Sends response back
 * 
 * Test cards:
 * - 4111111111111111 → Always APPROVE
 * - 4000000000000002 → Always DECLINE (insufficient funds)
 * - 4000000000000069 → Always DECLINE (expired card)
 * - 4000000000000077 → TIMEOUT (no response — tests timeout handling)
 */
@SpringBootApplication
@RequiredArgsConstructor
public class BankSimulatorApplication implements CommandLineRunner {

    private final TcpServer tcpServer;

    public static void main(String[] args) {
        SpringApplication.run(BankSimulatorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        tcpServer.start();
    }
}
