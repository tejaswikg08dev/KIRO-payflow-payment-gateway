# Phase 1 — Part 2: ISO 8583 Protocol Deep Dive

> This document explains ISO 8583 in full detail — the binary protocol that ALL card
> payment networks (Visa, Mastercard, RuPay, NPCI) use to communicate between banks.
> Understanding this protocol is a unique differentiator in payment interviews.

---

## 1. What Is ISO 8583?

ISO 8583 is an **international standard** for financial transaction card-originated messages.
It defines a message format that is used for:

- ATM withdrawals
- Card swipes at POS machines
- Online card payments (e-commerce)
- Balance inquiries
- PIN changes
- Reversals and chargebacks

**Every time you use a credit/debit card anywhere in the world, an ISO 8583 message
is created, sent across the network, and a response comes back — all in 2-3 seconds.**

---

## 2. Why Is It Binary (Not JSON/XML)?

| Format | Size for same data | Parse speed | Used by |
|--------|-------------------|-------------|---------|
| JSON | ~500 bytes | ~1ms | REST APIs, web apps |
| XML | ~800 bytes | ~2ms | SOAP, legacy enterprise |
| **ISO 8583** | **~150 bytes** | **~0.1ms** | **Banks, card networks** |

Banks need:
- **Minimum bandwidth** (millions of transactions/second across networks)
- **Minimum latency** (authorization in <200ms)
- **Compact format** (less data = faster transmission)

That's why they use a binary format, not human-readable text.

---

## 3. Message Structure

Every ISO 8583 message has 3 parts:

```
┌──────────────────────────────────────────────────────────────────┐
│                    ISO 8583 MESSAGE                                │
│                                                                    │
│  ┌──────────┐  ┌────────────────────┐  ┌──────────────────────┐ │
│  │   MTI    │  │      BITMAP        │  │     DATA FIELDS      │ │
│  │ (4 bytes)│  │  (8 or 16 bytes)   │  │  (variable length)   │ │
│  │          │  │                    │  │                      │ │
│  │ Message  │  │ Tells which fields │  │ Actual transaction   │ │
│  │ type     │  │ are present in     │  │ data (card, amount,  │ │
│  │          │  │ this message       │  │ merchant, etc.)      │ │
│  └──────────┘  └────────────────────┘  └──────────────────────┘ │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. MTI (Message Type Indicator) — First 4 Bytes

The MTI is a 4-digit number that tells WHAT TYPE of message this is.

### 4.1 MTI Structure

```
MTI = XYZW (4 digits)

X = Version
    0 = ISO 8583:1987 (most common)
    1 = ISO 8583:1993
    2 = ISO 8583:2003

Y = Message Class
    1 = Authorization (can this card pay?)
    2 = Financial (actually move money)
    3 = File actions
    4 = Reversal / Chargeback
    5 = Reconciliation
    8 = Network management

Z = Message Function
    0 = Request
    1 = Response
    2 = Advice (notification)
    3 = Advice response

W = Message Origin
    0 = Acquirer (merchant's side)
    1 = Acquirer repeat
    2 = Issuer (cardholder's bank)
    3 = Issuer repeat
```


### 4.2 MTI Types We Implement in PayFlow

| MTI | Class | Meaning | Example |
|-----|-------|---------|---------|
| **0100** | Auth Request | "Can this card pay ₹5000?" | Card payment authorization |
| **0110** | Auth Response | "Yes, approved. Auth code: A1B2C3" | Bank's answer |
| **0200** | Financial Request | "Debit ₹5000 from card now" | Actual money movement |
| **0210** | Financial Response | "Done. Transaction settled" | Confirmation |
| **0400** | Reversal Request | "Cancel the previous ₹5000 charge" | Timeout/error recovery |
| **0410** | Reversal Response | "Reversed. Money returned" | Reversal confirmed |
| **0800** | Network Mgmt Request | "Are you alive? (ping)" | Health check / sign-on |
| **0810** | Network Mgmt Response | "Yes, I'm alive" | Health confirmed |

### 4.3 How to Read an MTI

```
Example: 0100
├── 0 = Version: ISO 8583:1987
├── 1 = Class: Authorization message
├── 0 = Function: Request
└── 0 = Origin: Acquirer

So 0100 = "Authorization Request from Acquirer (1987 version)"

Example: 0110
├── 0 = Version: ISO 8583:1987
├── 1 = Class: Authorization message
├── 1 = Function: Response
└── 0 = Origin: Acquirer

So 0110 = "Authorization Response (answer to 0100)"
```

---

## 5. Bitmap — Which Fields Are Present?

### 5.1 What Is a Bitmap?

A bitmap is a **64-bit binary number** (8 bytes). Each bit represents whether a
specific data field is present in the message.

```
Bit position:  1  2  3  4  5  6  7  8  9  10 11 12 ... 64
Bitmap value:  1  1  0  1  0  0  1  0  0  0  1  1  ... 0

Meaning:
├── Bit 1 = 1 → Secondary bitmap present (extends to 128 fields)
├── Bit 2 = 1 → Field 2 (PAN / card number) IS present
├── Bit 3 = 0 → Field 3 (Processing code) is NOT present
├── Bit 4 = 1 → Field 4 (Amount) IS present
├── Bit 5 = 0 → Field 5 NOT present
├── Bit 6 = 0 → Field 6 NOT present
├── Bit 7 = 1 → Field 7 (Transmission date/time) IS present
├── Bit 11 = 1 → Field 11 (STAN) IS present
├── Bit 12 = 1 → Field 12 (Local time) IS present
└── ... and so on
```

### 5.2 Bitmap in Hex

The 64-bit bitmap is usually represented as 16 hexadecimal characters:

```
Binary:  0111 0010 0011 0100 0000 0000 ... (64 bits)
Hex:     7 2 3 4 0 0 0 0 ... (16 hex chars)

Example bitmap: "7234000000000000"
Means fields 2, 3, 4, 7, 11, 12, 14, 22, 25 are present
```

### 5.3 How to Parse a Bitmap (Step by Step)

```
Given hex bitmap: "F230040128C18000"

Step 1: Convert hex to binary
F = 1111    2 = 0010    3 = 0011    0 = 0000
0 = 0000    4 = 0100    0 = 0000    1 = 0001
2 = 0010    8 = 1000    C = 1100    1 = 0001
8 = 1000    0 = 0000    0 = 0000    0 = 0000

Step 2: Read bit positions left to right
Bit 1: 1 → Secondary bitmap exists
Bit 2: 1 → PAN present
Bit 3: 1 → Processing Code present
Bit 4: 1 → Amount present
Bit 5: 0 → not present
Bit 6: 0 → not present
Bit 7: 1 → Date/Time present
... continue for all 64 bits

Step 3: Only read data fields for bits that are 1
```

---

## 6. Data Fields — The Transaction Data

### 6.1 Fields We Use in PayFlow

| Field # | Name | Type | Max Length | Format | Example |
|---------|------|------|-----------|--------|---------|
| 2 | PAN (Card Number) | LLVAR | 19 | Numeric | 4111111111111111 |
| 3 | Processing Code | Fixed | 6 | Numeric | 000000 |
| 4 | Transaction Amount | Fixed | 12 | Numeric | 000000005000 |
| 7 | Transmission Date & Time | Fixed | 10 | MMDDhhmmss | 0719143022 |
| 11 | STAN | Fixed | 6 | Numeric | 123456 |
| 12 | Local Transaction Time | Fixed | 6 | hhmmss | 143022 |
| 13 | Local Transaction Date | Fixed | 4 | MMDD | 0719 |
| 14 | Expiration Date | Fixed | 4 | YYMM | 2812 |
| 22 | POS Entry Mode | Fixed | 3 | Numeric | 051 |
| 25 | POS Condition Code | Fixed | 2 | Numeric | 00 |
| 32 | Acquiring Institution ID | LLVAR | 11 | Numeric | 12345678 |
| 37 | Retrieval Reference Number | Fixed | 12 | Alphanum | 987654321012 |
| 38 | Authorization ID Response | Fixed | 6 | Alphanum | A1B2C3 |
| 39 | Response Code | Fixed | 2 | Alphanum | 00 |
| 41 | Card Acceptor Terminal ID | Fixed | 8 | Alphanum | TERM0001 |
| 42 | Card Acceptor ID Code | Fixed | 15 | Alphanum | MERCH00000000001 |
| 43 | Card Acceptor Name/Location | Fixed | 40 | Alphanum | Amazon India Mumbai IN |
| 49 | Currency Code (Transaction) | Fixed | 3 | Numeric | 356 |
| 54 | Additional Amounts | LLLVAR | 120 | Alphanum | Balance info |
| 55 | ICC Data (Chip/EMV) | LLLVAR | 999 | Binary | Chip data |

### 6.2 Field Types Explained

```
FIXED LENGTH:
├── Always exactly N characters
├── Example: Field 4 (Amount) = always 12 digits, padded with zeros
│   ₹50.00 = "000000005000" (amount in smallest unit — paise)
└── No length prefix needed

LLVAR (Variable, 2-digit length prefix):
├── First 2 digits = length of actual data
├── Then actual data follows
├── Example: Field 2 (PAN)
│   "164111111111111111" means:
│   "16" = 16 digits coming
│   "4111111111111111" = the actual card number
└── Max 99 characters

LLLVAR (Variable, 3-digit length prefix):
├── First 3 digits = length of actual data
├── Example: Field 55 (EMV data)
│   "032..." means 32 bytes of data follow
└── Max 999 characters
```

### 6.3 Processing Code (Field 3) Values

| Code | Meaning | Description |
|------|---------|-------------|
| 000000 | Purchase | Standard purchase transaction |
| 010000 | Cash Advance | Get cash from card |
| 200000 | Refund / Credit | Return money to card |
| 300000 | Balance Inquiry | Check available balance |
| 090000 | Purchase with Cashback | Buy + get cash |

### 6.4 POS Entry Mode (Field 22) Values

| Code | Meaning | Our Use |
|------|---------|---------|
| 010 | Manual entry (keyed) | Card number typed (phone order) |
| 051 | Chip card (ICC) | EMV chip read |
| 071 | Contactless chip (NFC) | Tap to pay |
| 081 | E-commerce | **Online payment (our primary use)** |
| 091 | Contactless magnetic stripe | Tap (mag stripe) |

---

## 7. Response Codes (Field 39) — Bank's Answer

### 7.1 Common Response Codes

| Code | Category | Meaning | Our Action |
|------|----------|---------|-----------|
| **00** | Approved | Transaction approved | Return SUCCESS |
| **01** | Referral | Refer to card issuer | Return DECLINED |
| **03** | Invalid | Invalid merchant | Return DECLINED |
| **05** | Declined | Do not honor (generic) | Return DECLINED |
| **12** | Invalid | Invalid transaction | Return DECLINED |
| **13** | Invalid | Invalid amount | Return ERROR |
| **14** | Invalid | Invalid card number | Return DECLINED |
| **30** | Error | Format error | Return ERROR (retry) |
| **41** | Blocked | Lost card — pick up | Return DECLINED + flag |
| **43** | Blocked | Stolen card — pick up | Return DECLINED + flag |
| **51** | Declined | Insufficient funds | Return DECLINED |
| **54** | Expired | Card expired | Return DECLINED |
| **55** | Invalid | Incorrect PIN | Return DECLINED |
| **57** | Not allowed | Transaction not permitted | Return DECLINED |
| **61** | Limit | Exceeds withdrawal limit | Return DECLINED |
| **65** | Limit | Activity count exceeded | Return DECLINED |
| **68** | Timeout | Response received too late | Return ERROR (retry) |
| **91** | Down | Issuer/switch not available | Return ERROR (retry) |
| **96** | Error | System malfunction | Return ERROR (retry) |

### 7.2 How We Handle Each Response

```java
switch (responseCode) {
    case "00" -> PaymentStatus.AUTHORIZED;           // Success!
    case "51" -> PaymentStatus.FAILED_INSUFFICIENT;  // Tell customer
    case "54" -> PaymentStatus.FAILED_EXPIRED;       // Card expired
    case "41", "43" -> {
        flagForFraud(payment);                       // Stolen/lost card
        return PaymentStatus.FAILED_BLOCKED;
    }
    case "91", "96", "68" -> {
        triggerRetryOnDifferentRoute(payment);       // Bank down, try another
        return PaymentStatus.PROCESSING;
    }
    default -> PaymentStatus.FAILED;
}
```


---

## 8. Complete Message Examples

### 8.1 Authorization Request (0100) — "Can this card pay?"

**Scenario:** Customer pays ₹5,000 at merchant "PayFlow Demo Store" using Visa card.

```
HUMAN-READABLE:
├── MTI: 0100 (Authorization Request)
├── Field 2:  4111111111111111 (Card number)
├── Field 3:  000000 (Purchase)
├── Field 4:  000000500000 (₹5000.00 in paise)
├── Field 7:  0719143022 (July 19, 14:30:22)
├── Field 11: 123456 (STAN — unique transaction trace)
├── Field 12: 143022 (local time)
├── Field 13: 0719 (local date)
├── Field 14: 2812 (card expiry: Dec 2028)
├── Field 22: 081 (e-commerce)
├── Field 25: 00 (normal transaction)
├── Field 32: 12345678 (acquiring bank ID)
├── Field 41: TERM0001 (terminal ID)
├── Field 42: MERCH00000000001 (merchant ID)
├── Field 43: PayFlow Demo Store     Mumbai       IN (merchant name/city)
├── Field 49: 356 (INR currency code)
└── Bitmap indicates fields 2,3,4,7,11,12,13,14,22,25,32,41,42,43,49 are present

WIRE FORMAT (simplified hex representation):
[MTI: 30313030]
[Bitmap: F230040128C18000]
[Field 2: 164111111111111111]  ← "16" prefix means 16 digits follow
[Field 3: 000000]
[Field 4: 000000500000]
[Field 7: 0719143022]
[Field 11: 123456]
[Field 12: 143022]
[Field 13: 0719]
[Field 14: 2812]
[Field 22: 081]
[Field 25: 00]
[Field 32: 0812345678]          ← "08" prefix means 8 digits follow
[Field 41: TERM0001]
[Field 42: MERCH00000000001]
[Field 43: PayFlow Demo Store     Mumbai       IN]
[Field 49: 356]
```

### 8.2 Authorization Response (0110) — "Approved"

```
HUMAN-READABLE:
├── MTI: 0110 (Authorization Response)
├── Field 2:  4111111111111111 (echoed back)
├── Field 3:  000000 (echoed back)
├── Field 4:  000000500000 (echoed back)
├── Field 7:  0719143022 (echoed back)
├── Field 11: 123456 (echoed back)
├── Field 37: 987654321012 (Retrieval Reference Number — bank assigned)
├── Field 38: A1B2C3 (Authorization Code — IMPORTANT: proves approval)
├── Field 39: 00 (Response Code — 00 means APPROVED!)
├── Field 41: TERM0001 (echoed back)
├── Field 42: MERCH00000000001 (echoed back)
└── Field 49: 356 (echoed back)
```

### 8.3 Authorization Response — "Declined (Insufficient Funds)"

```
├── MTI: 0110
├── ...same echoed fields...
├── Field 38: (empty — no auth code given)
├── Field 39: 51 (Insufficient funds — DECLINED)
└── No money was held
```

### 8.4 Reversal Request (0400) — "Cancel the previous transaction"

**When this happens:** Our system authorized a payment but then:
- Network timeout before we could confirm to customer
- Merchant decided to void
- Error in our system after authorization

```
├── MTI: 0400 (Reversal Request)
├── Field 2:  4111111111111111
├── Field 3:  000000
├── Field 4:  000000500000
├── Field 7:  0719143122 (current time)
├── Field 11: 123457 (NEW STAN for this reversal)
├── Field 37: 987654321012 (SAME RRN as original — links to original txn)
├── Field 38: A1B2C3 (original auth code)
├── Field 41: TERM0001
├── Field 42: MERCH00000000001
├── Field 49: 356
└── Message: "Please cancel transaction with RRN 987654321012"
```

---

## 9. How We Build ISO 8583 in Java

### 9.1 Message Class Design

```java
public class Iso8583Message {
    
    private String mti;                        // "0100", "0110", etc.
    private BitSet bitmap;                     // 64 or 128 bits
    private Map<Integer, String> fields;       // Field number → value
    
    // Build a message
    public static Iso8583Message authorizationRequest() {
        Iso8583Message msg = new Iso8583Message();
        msg.setMti("0100");
        msg.setField(2, "4111111111111111");   // PAN
        msg.setField(3, "000000");             // Purchase
        msg.setField(4, "000000500000");       // Amount in paise
        msg.setField(11, generateStan());      // Unique trace
        msg.setField(41, "TERM0001");          // Terminal
        msg.setField(42, "MERCH00000000001");  // Merchant
        msg.setField(49, "356");               // INR
        return msg;
    }
    
    // Convert to bytes for TCP transmission
    public byte[] pack() {
        byte[] mtiBytes = packMti();
        byte[] bitmapBytes = packBitmap();
        byte[] dataBytes = packFields();
        return concatenate(mtiBytes, bitmapBytes, dataBytes);
    }
    
    // Parse from bytes received via TCP
    public static Iso8583Message unpack(byte[] rawData) {
        Iso8583Message msg = new Iso8583Message();
        int offset = 0;
        msg.mti = parseMti(rawData, offset); offset += 4;
        msg.bitmap = parseBitmap(rawData, offset); offset += 16;
        msg.fields = parseFields(rawData, offset, msg.bitmap);
        return msg;
    }
}
```

### 9.2 Encoder (Java Object → Binary Bytes)

```java
public class Iso8583Encoder {
    
    public byte[] encode(Iso8583Message message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        
        // 1. Write MTI (4 bytes ASCII)
        out.write(message.getMti().getBytes());
        
        // 2. Calculate and write bitmap
        byte[] bitmap = calculateBitmap(message.getFields().keySet());
        out.write(bitmap);
        
        // 3. Write each field in order
        for (int fieldNum = 2; fieldNum <= 128; fieldNum++) {
            if (message.hasField(fieldNum)) {
                byte[] fieldData = packField(fieldNum, message.getField(fieldNum));
                out.write(fieldData);
            }
        }
        
        return out.toByteArray();
    }
    
    private byte[] packField(int fieldNum, String value) {
        FieldDefinition def = FieldDefinitions.get(fieldNum);
        
        switch (def.getType()) {
            case FIXED:
                // Right-pad or left-pad to fixed length
                return padToLength(value, def.getLength()).getBytes();
                
            case LLVAR:
                // Prefix with 2-digit length
                String prefix = String.format("%02d", value.length());
                return (prefix + value).getBytes();
                
            case LLLVAR:
                // Prefix with 3-digit length
                String prefix3 = String.format("%03d", value.length());
                return (prefix3 + value).getBytes();
                
            default:
                throw new IllegalArgumentException("Unknown field type");
        }
    }
}
```

### 9.3 Decoder (Binary Bytes → Java Object)

```java
public class Iso8583Decoder {
    
    public Iso8583Message decode(byte[] data) {
        Iso8583Message message = new Iso8583Message();
        int offset = 0;
        
        // 1. Read MTI (4 bytes)
        message.setMti(new String(data, offset, 4));
        offset += 4;
        
        // 2. Read bitmap (16 hex chars = 8 bytes)
        BitSet bitmap = parseBitmap(data, offset);
        offset += 16; // hex representation
        
        // 3. Read each field that bitmap says is present
        for (int fieldNum = 2; fieldNum <= 64; fieldNum++) {
            if (bitmap.get(fieldNum - 1)) { // bitmap is 0-indexed
                FieldDefinition def = FieldDefinitions.get(fieldNum);
                String value;
                
                switch (def.getType()) {
                    case FIXED:
                        value = new String(data, offset, def.getLength());
                        offset += def.getLength();
                        break;
                    case LLVAR:
                        int len2 = Integer.parseInt(new String(data, offset, 2));
                        offset += 2;
                        value = new String(data, offset, len2);
                        offset += len2;
                        break;
                    case LLLVAR:
                        int len3 = Integer.parseInt(new String(data, offset, 3));
                        offset += 3;
                        value = new String(data, offset, len3);
                        offset += len3;
                        break;
                    default:
                        throw new IllegalStateException();
                }
                
                message.setField(fieldNum, value);
            }
        }
        
        return message;
    }
}
```

---

## 10. TCP Communication (How Messages Travel)

### 10.1 Network Flow

```
Our Routing Service                              Bank Simulator
(TCP Client)                                     (TCP Server, Port 9000)
      │                                                │
      │  1. Open TCP connection                        │
      │───────────────── SYN ─────────────────────────►│
      │◄──────────────── SYN-ACK ─────────────────────│
      │───────────────── ACK ─────────────────────────►│
      │                                                │
      │  2. Send message length (4 bytes) + message    │
      │     [0000][0098][0100F230...data...]           │
      │───────────────── DATA ─────────────────────────►│
      │                                                │
      │                        3. Process and respond   │
      │                                                │
      │  4. Receive response length + response         │
      │     [0000][0076][0110B220...data...]           │
      │◄──────────────── DATA ─────────────────────────│
      │                                                │
      │  5. Close connection (or keep alive for more)  │
      │───────────────── FIN ─────────────────────────►│
      │                                                │
```

### 10.2 Message Framing

Since TCP is a stream protocol (no message boundaries), we need a way to know where one
message ends and the next begins. We use **length prefix**:

```
[2-byte length][message bytes]

Example:
[00][98] means next 98 bytes are the ISO 8583 message
Then read exactly 98 bytes → that's one complete message
```

### 10.3 Connection Management

```
In our project:
├── Connection pool (keep 3-5 connections open to bank simulator)
├── Timeout: 5 seconds (if no response, consider it failed)
├── Retry: Send 0400 reversal if timeout (to be safe)
├── Health check: Send 0800 every 30 seconds to verify connection alive
└── Reconnect: If connection drops, reconnect automatically
```

---

## 11. ISO 8583 in Our Architecture

```
Payment Service                    Routing Service                    Bank Simulator
      │                                  │                                 │
      │  REST: POST /internal/route      │                                 │
      │  {                               │                                 │
      │    paymentId: "pay_123",         │                                 │
      │    cardNumber: "4111...",        │                                 │
      │    amount: 5000,                 │                                 │
      │    merchantId: "merch_001",      │                                 │
      │    currency: "INR"              │                                 │
      │  }                               │                                 │
      │─────────────────────────────────►│                                 │
      │                                  │                                 │
      │                                  │ 1. Select best route             │
      │                                  │ 2. Build ISO 8583 (0100)         │
      │                                  │ 3. Encode to binary              │
      │                                  │ 4. Send via TCP ────────────────►│
      │                                  │                                 │
      │                                  │                                 │ 5. Decode
      │                                  │                                 │ 6. Process
      │                                  │                                 │ 7. Decide
      │                                  │                                 │ 8. Build 0110
      │                                  │                                 │ 9. Encode
      │                                  │                                 │
      │                                  │ 10. Receive response ◄───────────│
      │                                  │ 11. Decode binary                │
      │                                  │ 12. Check field 39               │
      │                                  │                                 │
      │  REST: Response                  │                                 │
      │  {                               │                                 │
      │    status: "AUTHORIZED",         │                                 │
      │    authCode: "A1B2C3",          │                                 │
      │    rrn: "987654321012",         │                                 │
      │    responseCode: "00",          │                                 │
      │    routeUsed: "HDFC_ACQ"        │                                 │
      │  }                               │                                 │
      │◄─────────────────────────────────│                                 │
```

---

## 12. Bank Simulator — What It Does

Our Bank Simulator acts as Visa/Mastercard/bank on the other end:

```
RULES:
├── Card 4111111111111111 → Always approve (test card)
├── Card 4000000000000002 → Always decline (insufficient funds)
├── Card 4000000000000069 → Always decline (expired)
├── Card 4000000000000077 → Simulate timeout (no response for 6s)
├── Amount > ₹100,000     → Decline (exceeds limit)
├── Random 5% of requests → Decline (simulates real-world failure rate)
├── Field 3 = "200000"    → Process as refund
└── Field 3 = "300000"    → Return balance in Field 54
```

---

## 13. Interview Questions This Document Answers

1. **"What is ISO 8583?"** → International standard for financial transaction messages
2. **"What is an MTI?"** → 4-digit code indicating message type (0100 = auth request)
3. **"What is a bitmap?"** → 64-bit field indicating which data fields are present
4. **"How does a card authorization work at the protocol level?"** → Build 0100, send TCP, get 0110
5. **"What's the difference between LLVAR and fixed-length fields?"** → Variable has length prefix
6. **"What response code means approved?"** → 00
7. **"What happens when bank times out?"** → Send 0400 reversal
8. **"Why binary instead of JSON?"** → Size (150B vs 500B) and speed (0.1ms vs 1ms parse)
9. **"How do you know which fields are in a message?"** → Bitmap tells you
10. **"What is STAN?"** → System Trace Audit Number, unique 6-digit transaction identifier

---

## Next Step

→ Continue to **`phase1-part3-architecture-and-design-decisions.md`**
