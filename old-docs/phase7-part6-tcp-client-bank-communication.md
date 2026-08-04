# Hands-On Guide — Phase 7 Part 6: TCP Client (Bank Communication)

## Goal

By the end of Part 6, you will have:
- BankTcpClient that sends ISO 8583 bytes to bank via TCP socket
- Message framing (2-byte length prefix)
- 5-second timeout handling
- Understanding of TCP vs HTTP communication
- Git commit

## Prerequisites

- Part 5 completed (encoder/decoder exist)

---

## Why TCP (Not HTTP) for Bank Communication?

```
HTTP (what REST APIs use):
├── Text-based headers (200 bytes overhead per request)
├── Connection per request (slow setup/teardown)
├── Content-Type negotiation, cookies, redirects
└── Good for: web APIs, microservice REST calls

TCP RAW (what banks use):
├── Zero overhead (just our data, nothing else)
├── Persistent connection (keep open, reuse)
├── Binary data (compact, fast)
└── Good for: high-volume financial transactions, low latency

ISO 8583 travels over TCP because:
├── Millions of transactions per second globally
├── Every byte of overhead = millions of wasted bytes
├── Latency must be <200ms (HTTP adds 50-100ms overhead)
└── Binary protocol doesn't need HTTP's text features
```

---

## Step 6.1: Create BankTcpClient

**Create file:** `routing-service/src/main/java/com/payflow/routing/service/BankTcpClient.java`

```java
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
 * Protocol (per message exchange):
 * 1. Open TCP connection to bank (host:port)
 * 2. SEND: [2-byte message length][ISO 8583 message bytes]
 * 3. RECEIVE: [2-byte response length][ISO 8583 response bytes]
 * 4. Close connection
 * 
 * The 2-byte length prefix tells the receiver how many bytes to read.
 * Without it, TCP is a stream — you wouldn't know where one message ends.
 * 
 * Timeout: 5 seconds. If bank doesn't respond → return null (caller handles).
 */
@Slf4j
@Component
public class BankTcpClient {

    @Value("${bank.simulator.host:localhost}")
    private String bankHost;
    // Default: localhost (for development)
    // Production: real bank network IP (e.g., "10.0.1.50")

    @Value("${bank.simulator.port:9000}")
    private int bankPort;
    // Our bank simulator listens on port 9000

    @Value("${bank.simulator.timeout-ms:5000}")
    private int timeoutMs;
    // 5 seconds — if bank hasn't responded by then, consider it a timeout
    // Real banks respond in 100-300ms typically

    /**
     * Send ISO 8583 message to bank and wait for response.
     * 
     * @param requestBytes The encoded ISO 8583 message (from Iso8583Encoder)
     * @return Response bytes from bank (to be decoded by Iso8583Decoder), or null if timeout
     */
    public byte[] sendAndReceive(byte[] requestBytes) {
        try (Socket socket = new Socket(bankHost, bankPort)) {
            // try-with-resources: socket auto-closes when block exits
            // new Socket(host, port): opens TCP connection (TCP 3-way handshake)
            
            socket.setSoTimeout(timeoutMs);
            // setSoTimeout: if read() blocks longer than 5000ms → SocketTimeoutException

            // Get I/O streams
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            // DataOutputStream: write primitive types in binary (writeShort, writeInt, etc.)
            DataInputStream in = new DataInputStream(socket.getInputStream());
            // DataInputStream: read primitive types in binary (readShort, readFully, etc.)

            // ===== SEND: [2-byte length][message bytes] =====
            out.writeShort(requestBytes.length);
            // writeShort: writes 2 bytes (big-endian unsigned short)
            // If message is 120 bytes: writes [0x00, 0x78]
            out.write(requestBytes);
            // write: sends the actual ISO 8583 message bytes
            out.flush();
            // flush: ensure all data is actually sent over the network (not buffered)

            log.debug("Sent {} bytes to bank {}:{}", requestBytes.length, bankHost, bankPort);

            // ===== RECEIVE: [2-byte length][response bytes] =====
            int responseLength = in.readUnsignedShort();
            // readUnsignedShort: reads 2 bytes, interprets as unsigned int (0-65535)
            // This tells us exactly how many bytes of response to read

            byte[] responseBytes = new byte[responseLength];
            in.readFully(responseBytes);
            // readFully: blocks until ALL responseLength bytes are received
            // Unlike read() which might return partial data

            log.debug("Received {} bytes from bank", responseLength);
            return responseBytes;

        } catch (SocketTimeoutException e) {
            // Bank didn't respond within 5 seconds
            log.warn("Bank TIMEOUT after {}ms ({}:{})", timeoutMs, bankHost, bankPort);
            return null;
            // Caller (RoutingEngine) will handle: send reversal, try next route

        } catch (IOException e) {
            // Connection refused, network error, etc.
            log.error("Bank communication ERROR: {} ({}:{})", e.getMessage(), bankHost, bankPort);
            return null;
            // Caller handles same as timeout
        }
    }
}
```

---

## Step 6.2: Message Framing Explained

```
WHY length prefix?

TCP is a STREAM protocol — it doesn't have message boundaries.
If you send 120 bytes then 80 bytes, the receiver might get:
├── 100 bytes (part of first message + part of second)
├── Then 100 bytes (rest of both)
└── No way to know where first message ends!

WITH 2-byte length prefix:
├── Send: [00][78] + [120 bytes of message]     ← 78 hex = 120 decimal
├── Receiver reads: first 2 bytes → "120 bytes coming"
├── Receiver reads: exactly 120 bytes → complete message!
├── Then reads next 2 bytes → length of next message
└── Clean separation guaranteed
```

---

## Step 6.3: Verify (With Bank Simulator Running)

### Start bank simulator first:
```cmd
cd bank-simulator
mvn spring-boot:run
# Console: "Bank Simulator started on port 9000"
```

### Start routing service:
```cmd
cd routing-service
mvn spring-boot:run
```

### Test the full flow:
```cmd
curl -X POST http://localhost:8084/internal/route ^
  -H "Content-Type: application/json" ^
  -d "{\"paymentId\":\"pay_test123\",\"cardNumber\":\"4111111111111111\",\"cardExpiry\":\"2812\",\"cardLast4\":\"1111\",\"amount\":500000,\"currency\":\"INR\",\"merchantId\":\"merch_test\"}"
```

**Expected (approved):**
```json
{
  "success": true,
  "data": {
    "success": true,
    "routeUsed": "HDFC_ACQ_01",
    "responseCode": "00",
    "authCode": "X9Y8Z7",
    "rrn": "987654321012"
  }
}
```

### Test with decline card:
```cmd
curl -X POST http://localhost:8084/internal/route ^
  -H "Content-Type: application/json" ^
  -d "{\"paymentId\":\"pay_decline\",\"cardNumber\":\"4000000000000002\",\"cardExpiry\":\"2812\",\"cardLast4\":\"0002\",\"amount\":500000,\"currency\":\"INR\",\"merchantId\":\"merch_test\"}"
```

**Expected (declined):**
```json
{
  "success": true,
  "data": {
    "success": false,
    "routeUsed": "HDFC_ACQ_01",
    "responseCode": "51",
    "failureReason": "Bank declined with response code: 51"
  }
}
```

---

## Step 6.4: Git Commit

```cmd
git add routing-service/src/main/java/com/payflow/routing/service/BankTcpClient.java
git commit -m "Phase 7 Part 6: TCP client - sends ISO 8583 bytes to bank, handles timeout"
```

---

## What We Built

| Component | Purpose |
|-----------|---------|
| `BankTcpClient.sendAndReceive()` | Opens TCP socket, sends bytes, receives response |
| Message framing | 2-byte length prefix before each message |
| Timeout handling | 5-second timeout → returns null (failover) |
| Error handling | Connection refused → returns null (failover) |

---

## Next Step

→ Continue to **Phase 7 Part 7: Bank Simulator (TCP Server)**
