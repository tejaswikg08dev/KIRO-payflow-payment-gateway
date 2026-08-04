# PayFlow — ISO 8583 Protocol Guide

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Overview

ISO 8583 is the **international standard for financial transaction messages**. It's used by Visa, Mastercard, RuPay, and banks worldwide. Understanding this protocol is essential for payment systems.

---

## 1. What is ISO 8583?

### Simple Explanation

Think of ISO 8583 like a **structured envelope** for financial messages:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Regular Letter vs ISO 8583                                │
│                                                                              │
│  Regular Letter:                                                             │
│  ───────────────                                                             │
│  "Dear Bank, please charge card 4111-1111-1111-1111                        │
│   for ₹5000 at Amazon Store"                                                │
│                                                                              │
│  ISO 8583 (Binary):                                                          │
│  ──────────────────                                                          │
│  0100                          ← Message type (authorization request)       │
│  F230040128A18000              ← Bitmap (which fields are present)          │
│  16411111111111111             ← Field 2: Card number                       │
│  000000500000                  ← Field 4: Amount (₹5000.00)                 │
│  1234567890123456789           ← Field 11: Trace number                     │
│  ...                                                                         │
│                                                                              │
│  Why binary? SPEED and SIZE                                                 │
│  - Faster to parse than JSON/XML                                            │
│  - Smaller message size                                                     │
│  - Banks process millions of transactions/second                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Message Structure

Every ISO 8583 message has **3 parts**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ISO 8583 Message Structure                                │
│                                                                              │
│  ┌────────────┬─────────────────────────┬───────────────────────────────┐  │
│  │    MTI     │        BITMAP           │           DATA FIELDS         │  │
│  │  4 bytes   │    8 or 16 bytes        │        Variable length        │  │
│  └────────────┴─────────────────────────┴───────────────────────────────┘  │
│                                                                              │
│  MTI: Message Type Indicator                                                │
│  ─────────────────────────────                                               │
│  Tells us what kind of message this is:                                     │
│  • 0100 = Authorization Request                                             │
│  • 0110 = Authorization Response                                            │
│  • 0400 = Reversal Request                                                  │
│                                                                              │
│  BITMAP: Which fields are present                                           │
│  ───────────────────────────────                                             │
│  64 bits (or 128 if extended)                                               │
│  Each bit = 1 field                                                         │
│  Bit 1 = 1 → Field 1 present                                               │
│  Bit 2 = 1 → Field 2 present                                               │
│                                                                              │
│  DATA FIELDS: Actual transaction data                                       │
│  ─────────────────────────────────────                                       │
│  Up to 128 fields (only present fields sent)                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. MTI (Message Type Indicator)

The MTI is a **4-digit code** that identifies the message type:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MTI Structure: X Y Z W                                    │
│                                                                              │
│  X = ISO Version                                                            │
│      0 = ISO 8583:1987                                                      │
│      1 = ISO 8583:1993                                                      │
│      2 = ISO 8583:2003                                                      │
│                                                                              │
│  Y = Message Class                                                          │
│      1 = Authorization                                                      │
│      2 = Financial                                                          │
│      4 = Reversal                                                           │
│      8 = Network Management                                                 │
│                                                                              │
│  Z = Message Function                                                       │
│      0 = Request                                                            │
│      1 = Response                                                           │
│      2 = Advice                                                             │
│                                                                              │
│  W = Transaction Originator                                                 │
│      0 = Acquirer                                                           │
│      1 = Repeat                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Common MTIs in PayFlow

| MTI | Meaning | When Used |
|-----|---------|-----------|
| 0100 | Authorization Request | Card payment auth |
| 0110 | Authorization Response | Bank's reply |
| 0200 | Financial Request | Direct debit |
| 0210 | Financial Response | Bank's reply |
| 0400 | Reversal Request | Cancel/timeout |
| 0410 | Reversal Response | Bank confirms |
| 0800 | Network Management | Health check |
| 0810 | Network Response | Health reply |

---

## 4. Bitmap

The bitmap indicates **which fields are present** in the message.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Bitmap Example                                            │
│                                                                              │
│  Bitmap: F230040128A18000 (hexadecimal)                                     │
│                                                                              │
│  Convert to binary:                                                          │
│  F    2    3    0    0    4    0    1    2    8    A    1    8    0    0    0│
│  1111 0010 0011 0000 0000 0100 0000 0001 0010 1000 1010 0001 1000 0000 0000 0000│
│  │││││││││││││││                                                            │
│  │││││││└─── Bit 1 = 1 → Secondary bitmap present                          │
│  ││││││└──── Bit 2 = 1 → Field 2 (Card Number) present                     │
│  │││││└───── Bit 3 = 1 → Field 3 (Processing Code) present                 │
│  ││││└────── Bit 4 = 1 → Field 4 (Amount) present                          │
│  │││└─────── Bit 5 = 0 → Field 5 NOT present                               │
│  ││└──────── Bit 6 = 0 → Field 6 NOT present                               │
│  │└───────── Bit 7 = 1 → Field 7 (Date/Time) present                       │
│  └────────── Bit 8 = 0 → Field 8 NOT present                               │
│                                                                              │
│  Only send fields where bit = 1                                             │
│  This saves bandwidth (don't send empty fields)                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Data Fields

ISO 8583 defines **128 fields** (0-127). Each field has a specific purpose.

### Key Fields We Use

| Field | Name | Length | Format | Example |
|-------|------|--------|--------|---------|
| 2 | Primary Account Number (PAN) | 19 | LLVAR | 4111111111111111 |
| 3 | Processing Code | 6 | N | 000000 |
| 4 | Transaction Amount | 12 | N | 000000500000 |
| 7 | Transmission Date/Time | 10 | N | 0804123045 |
| 11 | System Trace Number | 6 | N | 123456 |
| 12 | Local Transaction Time | 6 | N | 123045 |
| 13 | Local Transaction Date | 4 | N | 0804 |
| 14 | Expiration Date | 4 | N | 2512 |
| 22 | POS Entry Mode | 3 | N | 051 |
| 23 | Card Sequence Number | 3 | N | 001 |
| 35 | Track 2 Data | 37 | LLVAR | (magnetic stripe) |
| 37 | Retrieval Reference | 12 | AN | 012345678901 |
| 38 | Authorization Code | 6 | AN | AUTH01 |
| 39 | Response Code | 2 | AN | 00 |
| 41 | Terminal ID | 8 | AN | TERM0001 |
| 42 | Merchant ID | 15 | AN | MERCHANT001 |
| 43 | Merchant Name/Location | 40 | ANS | Amazon India |
| 49 | Currency Code | 3 | N | 356 (INR) |

### Field Types

| Type | Meaning | Characters |
|------|---------|------------|
| N | Numeric | 0-9 |
| AN | Alpha-Numeric | A-Z, 0-9 |
| ANS | Alpha-Numeric-Special | A-Z, 0-9, symbols |
| LLVAR | Variable length (2-digit length prefix) | varies |
| LLLVAR | Variable length (3-digit length prefix) | varies |

---

## 6. Field Encoding

### LLVAR Example (Field 2 - Card Number)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LLVAR Encoding                                            │
│                                                                              │
│  Card Number: 4111111111111111 (16 digits)                                  │
│                                                                              │
│  Encoded as:                                                                 │
│  ┌────────────────────────────────────────┐                                 │
│  │ 16 │ 4111111111111111                  │                                 │
│  │ LL │ VAR (variable content)            │                                 │
│  └────────────────────────────────────────┘                                 │
│                                                                              │
│  LL = "16" (length of card number)                                          │
│  VAR = actual card number                                                   │
│                                                                              │
│  Final bytes: 1 6 4 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1                          │
│                                                                              │
│  WHY LLVAR?                                                                  │
│  Card numbers can be 13-19 digits                                           │
│  Variable length saves space                                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Response Codes (Field 39)

| Code | Meaning | Action |
|------|---------|--------|
| 00 | Approved | Success! |
| 01 | Refer to issuer | Manual approval needed |
| 05 | Do not honor | Generic decline |
| 12 | Invalid transaction | Bad request |
| 13 | Invalid amount | Amount issue |
| 14 | Invalid card number | Card doesn't exist |
| 41 | Lost card | Card reported lost |
| 43 | Stolen card | Card reported stolen |
| 51 | Insufficient funds | Not enough balance |
| 54 | Expired card | Card expired |
| 55 | Incorrect PIN | Wrong PIN |
| 61 | Exceeds limit | Over daily/txn limit |
| 91 | Issuer unavailable | Bank system down |
| 96 | System malfunction | Technical error |

---

## 8. Complete Message Example

### Authorization Request (0100)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Authorization Request                                     │
│                                                                              │
│  Scenario: Customer pays ₹5,000 with Visa card at Amazon                   │
│                                                                              │
│  Raw Message (hex):                                                         │
│  0100                              ← MTI                                    │
│  F230040128A18000                  ← Bitmap                                 │
│  164111111111111111                ← Field 2: Card (LLVAR)                  │
│  000000                            ← Field 3: Processing code              │
│  000000500000                      ← Field 4: Amount (₹5000.00)            │
│  0804123045                        ← Field 7: DateTime                      │
│  123456                            ← Field 11: Trace                        │
│  123045                            ← Field 12: Time                         │
│  0804                              ← Field 13: Date                         │
│  2512                              ← Field 14: Expiry (Dec 2025)           │
│  051                               ← Field 22: Entry mode (e-commerce)     │
│  012345678901                      ← Field 37: Reference                    │
│  TERM0001                          ← Field 41: Terminal ID                  │
│  MERCHANT00001234                  ← Field 42: Merchant ID                  │
│  Amazon India Mumbai              ← Field 43: Merchant name                │
│  356                               ← Field 49: Currency (INR)              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Authorization Response (0110)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Authorization Response                                    │
│                                                                              │
│  0110                              ← MTI (response)                         │
│  7230040128A18000                  ← Bitmap                                 │
│  164111111111111111                ← Field 2: Card (echoed back)            │
│  000000                            ← Field 3: Processing code              │
│  000000500000                      ← Field 4: Amount                        │
│  0804123045                        ← Field 7: DateTime                      │
│  123456                            ← Field 11: Trace                        │
│  123045                            ← Field 12: Time                         │
│  0804                              ← Field 13: Date                         │
│  012345678901                      ← Field 37: Reference                    │
│  AUTH01                            ← Field 38: Auth Code ★ NEW             │
│  00                                ← Field 39: Response "Approved" ★ NEW   │
│  TERM0001                          ← Field 41: Terminal ID                  │
│  MERCHANT00001234                  ← Field 42: Merchant ID                  │
│  356                               ← Field 49: Currency                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. TCP Communication

ISO 8583 messages are sent over **TCP** (not HTTP).

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TCP Message Format                                        │
│                                                                              │
│  ┌────────────────┬───────────────────────────────────────────────────────┐│
│  │ Length Header  │              ISO 8583 Message                          ││
│  │   (2 bytes)    │                                                        ││
│  └────────────────┴───────────────────────────────────────────────────────┘│
│                                                                              │
│  Length Header: Total bytes in message (big-endian)                        │
│  Example: 0x00 0x8A = 138 bytes                                            │
│                                                                              │
│  WHY length header?                                                         │
│  TCP is a stream (no message boundaries)                                   │
│  Length tells receiver how many bytes to read                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### PayFlow TCP Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TCP Communication Flow                                    │
│                                                                              │
│  PayFlow (Routing Service)                     Bank Simulator               │
│           │                                           │                     │
│           │ 1. Open TCP connection                    │                     │
│           │──────────────────────────────────────────▶│                     │
│           │                                           │                     │
│           │ 2. Send: [2-byte length][ISO 8583 request]│                     │
│           │──────────────────────────────────────────▶│                     │
│           │                                           │                     │
│           │ 3. Wait for response (timeout: 5 sec)    │                     │
│           │                                           │                     │
│           │ 4. Receive: [2-byte length][ISO 8583 response]                 │
│           │◀──────────────────────────────────────────│                     │
│           │                                           │                     │
│           │ 5. Close connection (or keep-alive)       │                     │
│           │                                           │                     │
│                                                                              │
│  If no response in 5 seconds:                                               │
│  → Send reversal message (MTI 0400)                                        │
│  → Mark transaction as failed                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. PayFlow Implementation

In PayFlow, we implement ISO 8583 using **custom Java classes**:

```
routing-service/
└── src/main/java/com/payflow/routing/
    └── iso8583/
        ├── Iso8583Message.java      ← Message object
        ├── Iso8583Encoder.java      ← Java → Binary
        ├── Iso8583Decoder.java      ← Binary → Java
        ├── FieldDefinition.java     ← Field metadata
        ├── FieldDefinitions.java    ← All 128 field definitions
        └── FieldType.java           ← FIXED, LLVAR, LLLVAR
```

This will be implemented in **Sprint 4**.

---

## Next Steps

**Continue to:** [06-sprint-roadmap.md](./06-sprint-roadmap.md)

This will show the complete learning path across all 12 sprints.

---

**End of ISO 8583 Protocol Guide**

*Next: [06-sprint-roadmap.md](./06-sprint-roadmap.md)*
