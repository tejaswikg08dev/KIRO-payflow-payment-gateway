# Hands-On Guide — Phase 7 Part 7: Bank Simulator (TCP Server)

## Goal

By the end of Part 7, you will have:
- Bank Simulator running as a TCP server on port 9000
- TcpServer that accepts connections and reads ISO 8583 messages
- Iso8583RequestHandler that decides approve/decline based on card number rules
- Test cards: 4111→approve, 4000...02→decline, 4000...77→timeout
- Full round-trip working: routing-service → bank-simulator → response
- Git commit

## Prerequisites

- Part 6 completed (BankTcpClient can connect and send bytes)

---

## What Is the Bank Simulator?

```
IN PRODUCTION: Routing service connects to real Visa/Mastercard/NPCI networks.
FOR OUR PROJECT: We built a "fake bank" that behaves the same way.

Bank Simulator:
├── Listens on TCP port 9000 (like a real bank would)
├── Receives ISO 8583 messages (same format real banks expect)
├── Applies configurable rules (approve/decline based on card number)
├── Returns ISO 8583 responses (same format real banks send)
├── Simulates latency (100-300ms delay — realistic bank processing time)
└── Can simulate timeout (for timeout-handling tests)

TEST CARDS:
├── 4111 1111 1111 1111 → Always APPROVE (Visa test card)
├── 4000 0000 0000 0002 → Always DECLINE (code 51 — insufficient funds)
├── 4000 0000 0000 0069 → Always DECLINE (code 54 — expired card)
├── 4000 0000 0000 0077 → TIMEOUT (no response — tests failover)
├── 4000 0000 0000 0036 → DECLINE (code 41 — lost card)
├── 5500 0000 0000 0004 → Always APPROVE (Mastercard test)
├── 6521 0000 0000 0005 → Always APPROVE (RuPay test)
└── Any other card → 90% approve, 10% random decline
```

---

## Source Code

The bank-simulator source code was created in an earlier session. Key files:

```
bank-simulator/
├── pom.xml
└── src/main/java/com/payflow/simulator/
    ├── BankSimulatorApplication.java      ← Starts TCP server on port 9000
    ├── server/TcpServer.java              ← Accepts TCP connections, reads messages
    └── handler/Iso8583RequestHandler.java ← Parses request, applies rules, builds response
```

### How TcpServer Works:
```java
// 1. Open ServerSocket on port 9000
ServerSocket serverSocket = new ServerSocket(9000);
// 2. Accept incoming connection
Socket clientSocket = serverSocket.accept();
// 3. Read 2-byte length prefix
int messageLength = in.readUnsignedShort();
// 4. Read exactly messageLength bytes
byte[] requestBytes = new byte[messageLength];
in.readFully(requestBytes);
// 5. Process (apply rules, generate response)
byte[] responseBytes = requestHandler.handleRequest(requestBytes);
// 6. Send response: [2-byte length][response bytes]
out.writeShort(responseBytes.length);
out.write(responseBytes);
```

### How Request Handler Decides:
```java
// Extract card number from ISO 8583 message
String pan = extractPan(requestBytes);

// Apply rules
if (pan.equals("4000000000000077")) return null;  // Timeout simulation
if (pan.equals("4000000000000002")) responseCode = "51"; // Insufficient funds
if (pan.startsWith("4111"))          responseCode = "00"; // Approved
// ... generate response with auth code and RRN
```

---

## How to Run

```cmd
# Terminal 1: Start bank simulator FIRST
cd bank-simulator
mvn spring-boot:run
# You'll see: "Bank Simulator started on port 9000"

# Terminal 2: Start routing service
cd routing-service
mvn spring-boot:run
# Now routing can connect to bank!
```

---

## Git Commit

```cmd
git add bank-simulator/
git commit -m "Phase 7 Part 7: Bank simulator - TCP server with approve/decline rules"
```

---

## Next Step

→ Continue to **Phase 7 Part 8: End-to-End Integration**
