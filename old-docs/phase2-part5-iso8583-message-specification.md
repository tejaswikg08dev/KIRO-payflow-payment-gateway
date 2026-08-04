# Phase 2 — Part 5: ISO 8583 Message Specification

> This document defines the EXACT messages our system sends and receives.
> Think of this as a contract — routing-service builds these messages,
> bank-simulator expects and responds with these messages.
> In Phase 7, we'll write the Java code that implements this spec.

---

## 1. Overview — Messages We Support

Our system implements a subset of ISO 8583. We don't need all 128 fields
or all message types — just the ones relevant to online card payments.

### 1.1 Message Types We Implement

| MTI | Name | Direction | Purpose | When Used |
|-----|------|-----------|---------|-----------|
| 0100 | Authorization Request | PayFlow → Bank | "Can this card pay ₹X?" | Customer clicks "Pay" |
| 0110 | Authorization Response | Bank → PayFlow | "Yes/No, here's auth code" | Bank answers |
| 0200 | Financial Request | PayFlow → Bank | "Actually charge ₹X" | Capture (money moves) |
| 0210 | Financial Response | Bank → PayFlow | "Charged successfully" | Bank confirms |
| 0400 | Reversal Request | PayFlow → Bank | "Cancel transaction X" | Void or timeout recovery |
| 0410 | Reversal Response | Bank → PayFlow | "Cancelled" | Bank confirms reversal |
| 0800 | Network Management Request | PayFlow → Bank | "Are you alive?" | Health check (every 30s) |
| 0810 | Network Management Response | Bank → PayFlow | "Yes, I'm alive" | Bank confirms alive |

### 1.2 When Each Message Is Used

```
SCENARIO 1: Successful Card Payment
Customer clicks Pay → [0100 sent] → [0110 received, code 00] → AUTHORIZED

SCENARIO 2: Capture (Money Moves)
Merchant captures → [0200 sent] → [0210 received, code 00] → CAPTURED

SCENARIO 3: Declined
Customer clicks Pay → [0100 sent] → [0110 received, code 51] → FAILED

SCENARIO 4: Timeout Recovery
Customer clicks Pay → [0100 sent] → [no response in 5s] → [0400 sent] → [0410 received] → REVERSED

SCENARIO 5: Void
Merchant voids → [0400 sent with original auth details] → [0410 received] → VOIDED

SCENARIO 6: Health Check (Background)
Every 30 seconds → [0800 sent] → [0810 received] → Connection alive ✓
```

---

## 2. Field Definitions (Our Subset)

### 2.1 Complete Field Table

These are ALL the fields our system uses. We don't implement the full 128 ISO fields —
only what's needed for online card payments.

| Field # | Name | Length | Type | Format | Present In |
|---------|------|--------|------|--------|-----------|
| 2 | Primary Account Number (PAN) | 13-19 | LLVAR | Numeric | 0100, 0110, 0200, 0400 |
| 3 | Processing Code | 6 | Fixed | Numeric | All |
| 4 | Transaction Amount | 12 | Fixed | Numeric (in paise) | All |
| 7 | Transmission Date & Time | 10 | Fixed | MMDDhhmmss | All |
| 11 | System Trace Audit Number (STAN) | 6 | Fixed | Numeric | All |
| 12 | Local Transaction Time | 6 | Fixed | hhmmss | 0100, 0200 |
| 13 | Local Transaction Date | 4 | Fixed | MMDD | 0100, 0200 |
| 14 | Expiration Date | 4 | Fixed | YYMM | 0100 |
| 22 | POS Entry Mode | 3 | Fixed | Numeric | 0100 |
| 25 | POS Condition Code | 2 | Fixed | Numeric | 0100 |
| 32 | Acquiring Institution ID | 6-11 | LLVAR | Numeric | 0100, 0200, 0400 |
| 37 | Retrieval Reference Number | 12 | Fixed | Alphanumeric | 0110, 0210, 0410 |
| 38 | Authorization ID Response | 6 | Fixed | Alphanumeric | 0110 (if approved) |
| 39 | Response Code | 2 | Fixed | Alphanumeric | 0110, 0210, 0410, 0810 |
| 41 | Card Acceptor Terminal ID | 8 | Fixed | Alphanumeric | 0100, 0200, 0400 |
| 42 | Card Acceptor ID Code | 15 | Fixed | Alphanumeric | 0100, 0200, 0400 |
| 43 | Card Acceptor Name/Location | 40 | Fixed | Alphanumeric | 0100 |
| 49 | Transaction Currency Code | 3 | Fixed | Numeric | 0100, 0200, 0400 |
| 54 | Additional Amounts | 12-120 | LLLVAR | Alphanumeric | 0110 (balance) |

### 2.2 Field Type Explanation (Recap)

```
FIXED:
├── Always exactly N characters
├── Padded with zeros (numeric) or spaces (alphanumeric) if shorter
├── No length prefix in the wire format
└── Example: Field 4 (Amount, 12 chars) → "000000500000" for ₹5000.00

LLVAR (2-digit length prefix):
├── First 2 characters = length of data that follows
├── Then the actual data
├── Maximum length: 99 characters
└── Example: Field 2 (PAN) → "16" + "4111111111111111"
                                ↑ means 16 chars follow

LLLVAR (3-digit length prefix):
├── First 3 characters = length of data that follows
├── Then the actual data
├── Maximum length: 999 characters
└── Example: Field 54 → "012" + "100000050000"
                          ↑ means 12 chars follow
```

---

## 3. Message Specifications (Byte-Level Detail)

### 3.1 Authorization Request (0100) — Full Specification

**When:** Customer submits card payment on checkout page.
**Who sends:** Our Routing Service (acting as acquirer/gateway).
**Who receives:** Bank Simulator (acting as Visa/Mastercard network → Issuing bank).

**Fields included:**

| Field | Value | Explanation |
|-------|-------|-------------|
| MTI | 0100 | "I'm an authorization request" |
| 2 | 4111111111111111 | Customer's card number |
| 3 | 000000 | Processing code: purchase |
| 4 | 000000500000 | Amount: ₹5000.00 (in paise, 12 digits) |
| 7 | 0719143022 | Transmission: July 19, 14:30:22 |
| 11 | 123456 | STAN: unique trace for this transaction |
| 12 | 143022 | Local time: 14:30:22 |
| 13 | 0719 | Local date: July 19 |
| 14 | 2812 | Card expiry: Dec 2028 |
| 22 | 081 | POS entry: e-commerce (online) |
| 25 | 00 | Condition: normal transaction |
| 32 | 12345678 | Our acquiring institution ID |
| 41 | TERM0001 | Terminal ID (our system identifier) |
| 42 | MERCH00000000001 | Merchant ID |
| 43 | PayFlow Demo Store Mumbai IN | Merchant name + city + country |
| 49 | 356 | Currency: INR (India) |

**Bitmap calculation:**

```
Fields present: 2, 3, 4, 7, 11, 12, 13, 14, 22, 25, 32, 41, 42, 43, 49

Bit positions (1-indexed):
Bit 2:  ON    Bit 3:  ON    Bit 4:  ON    Bit 7:  ON
Bit 11: ON    Bit 12: ON    Bit 13: ON    Bit 14: ON
Bit 22: ON    Bit 25: ON    Bit 32: ON    Bit 41: ON
Bit 42: ON    Bit 43: ON    Bit 49: ON

Binary (64 bits):
0111 0010 0011 1000 0100 0001 0000 0001 0100 1100 0000 0001 0000 0000 0000 0000

Hex bitmap: 723841014C010000
```

**Complete wire message (human-readable representation):**

```
[MTI]     0100
[BITMAP]  7238410014C01000
[F02]     164111111111111111        ← LL=16, then 16 digits of PAN
[F03]     000000                     ← Fixed 6: purchase
[F04]     000000500000               ← Fixed 12: amount in paise
[F07]     0719143022                 ← Fixed 10: date+time
[F11]     123456                     ← Fixed 6: STAN
[F12]     143022                     ← Fixed 6: time
[F13]     0719                       ← Fixed 4: date
[F14]     2812                       ← Fixed 4: expiry YYMM
[F22]     081                        ← Fixed 3: e-commerce
[F25]     00                         ← Fixed 2: normal
[F32]     0812345678                 ← LL=08, then 8 digits
[F41]     TERM0001                   ← Fixed 8
[F42]     MERCH00000000001           ← Fixed 15
[F43]     PayFlow Demo Store     Mumbai       IN  ← Fixed 40 (space-padded)
[F49]     356                        ← Fixed 3: INR
```

---

### 3.2 Authorization Response (0110) — APPROVED

**When:** Bank approves the transaction.
**Who sends:** Bank Simulator.
**Who receives:** Our Routing Service.

**Fields included:**

| Field | Value | Explanation |
|-------|-------|-------------|
| MTI | 0110 | "I'm an authorization response" |
| 2 | 4111111111111111 | Echo back the PAN |
| 3 | 000000 | Echo back processing code |
| 4 | 000000500000 | Echo back amount |
| 7 | 0719143022 | Echo back timestamp |
| 11 | 123456 | Echo back STAN (links request ↔ response) |
| 37 | 987654321012 | RRN: bank-generated reference (for tracking) |
| 38 | A1B2C3 | Auth code: PROOF of approval (save this!) |
| 39 | 00 | Response code: **APPROVED** ✅ |
| 41 | TERM0001 | Echo back terminal |
| 42 | MERCH00000000001 | Echo back merchant |
| 49 | 356 | Echo back currency |

**Our system reads field 39:**
- "00" → Payment AUTHORIZED → tell customer "success" → save auth_code
- Anything else → Payment FAILED → tell customer "declined" → log reason

---

### 3.3 Authorization Response (0110) — DECLINED

**Same structure, different field 39:**

| Field | Value | Explanation |
|-------|-------|-------------|
| 39 | 51 | **DECLINED — Insufficient funds** |
| 38 | (empty/spaces) | No auth code given (was declined) |

**Other decline codes we handle:**

| Field 39 | Meaning | Message to Customer |
|----------|---------|-------------------|
| 00 | Approved | "Payment successful!" |
| 05 | Do not honor | "Payment declined. Please try another card." |
| 14 | Invalid card | "Invalid card number. Please check and retry." |
| 41 | Lost card | "This card has been reported lost." |
| 43 | Stolen card | "This card has been reported stolen." |
| 51 | Insufficient funds | "Insufficient balance. Please try another card." |
| 54 | Expired card | "Your card has expired." |
| 55 | Incorrect PIN | "Incorrect PIN entered." |
| 61 | Exceeds limit | "Transaction amount exceeds your card limit." |
| 91 | Bank unavailable | "Bank is temporarily unavailable. Please retry." |

---

### 3.4 Financial Request (0200) — Capture

**When:** Merchant calls POST /capture. We tell the bank to actually move money.
**Fields:** Same as 0100 but MTI = 0200, and includes Field 38 (auth code from 0110).

| Field | Value | Notes |
|-------|-------|-------|
| MTI | 0200 | Financial transaction request |
| 2 | 4111111111111111 | Same card |
| 3 | 000000 | Purchase |
| 4 | 000000500000 | Amount to capture (can be ≤ authorized amount) |
| 7 | 0719150000 | Current timestamp |
| 11 | 123457 | NEW STAN (different from 0100's STAN) |
| 32 | 12345678 | Our acquirer ID |
| 37 | 987654321012 | SAME RRN from 0110 (links to original auth) |
| 38 | A1B2C3 | SAME auth code from 0110 (proves we were authorized) |
| 41 | TERM0001 | Terminal |
| 42 | MERCH00000000001 | Merchant |
| 49 | 356 | Currency |

---

### 3.5 Reversal Request (0400) — Void or Timeout Recovery

**When:** 
- Merchant calls POST /void (cancel before capture)
- Our system times out waiting for 0110 (safety reversal)
- Error in our system after authorization (undo the auth)

| Field | Value | Notes |
|-------|-------|-------|
| MTI | 0400 | Reversal request |
| 2 | 4111111111111111 | Same card |
| 3 | 000000 | Same processing code as original |
| 4 | 000000500000 | Same amount as original |
| 7 | 0719151000 | Current timestamp |
| 11 | 123458 | NEW STAN for reversal |
| 37 | 987654321012 | SAME RRN from original (links reversal to original) |
| 38 | A1B2C3 | Auth code from original (if we had one) |
| 41 | TERM0001 | Terminal |
| 42 | MERCH00000000001 | Merchant |
| 49 | 356 | Currency |

**Key point:** The RRN (Field 37) LINKS the reversal to the original transaction.
The bank uses this to find the original and undo it.

---

### 3.6 Network Management (0800/0810) — Health Check

**Purpose:** Verify TCP connection is alive and bank simulator is responsive.
Sent every 30 seconds automatically by our system.

**Request (0800):**
| Field | Value |
|-------|-------|
| MTI | 0800 |
| 7 | 0719143100 (current timestamp) |
| 11 | 999999 (special STAN for network mgmt) |

**Response (0810):**
| Field | Value |
|-------|-------|
| MTI | 0810 |
| 7 | 0719143100 (echoed) |
| 11 | 999999 (echoed) |
| 39 | 00 (all good) |

If 0810 doesn't come back within 3 seconds → connection is dead → reconnect.

---

## 4. Bank Simulator Rules

Our bank simulator is a TCP server that receives ISO 8583 messages and responds.
Here are the rules it follows:

### 4.1 Authorization Rules (When 0100 Comes In)

```
RULE 1: Check card number (Field 2)
├── Starts with "4111111111111111" → APPROVE (test card)
├── Starts with "4000000000000002" → DECLINE code 51 (insufficient funds)
├── Starts with "4000000000000069" → DECLINE code 54 (expired)
├── Starts with "4000000000000077" → NO RESPONSE (simulate timeout)
├── Starts with "4000000000000036" → DECLINE code 41 (lost card)
├── Starts with "5500" → APPROVE (Mastercard test)
├── Starts with "6521" → APPROVE (RuPay test)
└── Any other → Random: 90% approve, 10% decline (code 05)

RULE 2: Check amount (Field 4)
├── Amount > 10,000,000 (₹1,00,000) → DECLINE code 61 (exceeds limit)
├── Amount = 0 → DECLINE code 13 (invalid amount)
└── Amount 1 to 10,000,000 → proceed with Rule 1

RULE 3: Simulate latency
├── Generate random delay: 100ms to 300ms
└── This simulates real-world network + bank processing time

RULE 4: Generate auth code (if approved)
├── Random 6-character alphanumeric: "A1B2C3", "X9Y8Z7", etc.
└── This is Field 38 in the response

RULE 5: Generate RRN (always)
├── 12-digit numeric, based on timestamp + random
└── This is Field 37 in the response
```

### 4.2 Financial Rules (When 0200 Comes In)

```
RULE 1: Verify auth code (Field 38) exists in our records
├── Found → APPROVE (code 00)
└── Not found → DECLINE (code 12, invalid transaction)

RULE 2: Verify amount ≤ original authorized amount
├── OK → APPROVE
└── Greater → DECLINE (code 13, invalid amount)
```

### 4.3 Reversal Rules (When 0400 Comes In)

```
RULE 1: Always approve reversals (code 00)
└── In real world, bank always tries to reverse
    (better to reverse a non-existent txn than leave a hanging auth)
```

---

## 5. Error Handling & Edge Cases

### 5.1 Timeout Handling

```
Our system sends 0100:
├── Wait up to 5 seconds for 0110
├── IF response received in time:
│   └── Process normally (approve/decline based on field 39)
├── IF no response in 5 seconds (TIMEOUT):
│   ├── 1. DON'T tell customer "approved" (we don't know!)
│   ├── 2. DON'T tell customer "failed" (maybe bank approved but response got lost)
│   ├── 3. Send 0400 reversal (to be safe — if bank approved, this cancels it)
│   ├── 4. Wait up to 3 seconds for 0410
│   ├── 5. Tell customer: "Payment could not be completed. Please try again."
│   └── 6. Log everything for investigation
```

### 5.2 Duplicate STAN Prevention

```
STAN (Field 11) must be unique per transaction per day.
If we accidentally send the same STAN twice, bank may:
├── Reject as duplicate
├── Or process twice (double charge!) ← DANGEROUS

Our solution:
├── Generate STAN as: first 6 digits of UUID (guaranteed unique)
├── Track sent STANs in Redis (TTL: 24 hours)
└── Never reuse a STAN within 24 hours
```

### 5.3 Partial Response (Connection Drops Mid-Response)

```
We receive partial bytes (message cut off):
├── 1. Cannot parse → treat as timeout
├── 2. Send 0400 reversal
├── 3. Mark payment as FAILED with reason "partial_response"
└── 4. Alert ops team
```

---

## 6. Test Cards (For Development & Demo)

| Card Number | Behavior | Use For |
|-------------|----------|---------|
| 4111 1111 1111 1111 | Always APPROVED | Happy path testing |
| 4000 0000 0000 0002 | Always DECLINED (51 — insufficient) | Decline testing |
| 4000 0000 0000 0069 | Always DECLINED (54 — expired) | Expiry testing |
| 4000 0000 0000 0077 | TIMEOUT (no response) | Timeout handling test |
| 4000 0000 0000 0036 | DECLINED (41 — lost card) | Fraud flag testing |
| 5500 0000 0000 0004 | APPROVED (Mastercard) | MC routing test |
| 6521 0000 0000 0005 | APPROVED (RuPay) | RuPay routing test |
| 4000 0000 0000 0044 | Random (90% pass) | Realistic testing |

**All test cards use:**
- Expiry: Any future date (e.g., 12/28)
- CVV: Any 3 digits (e.g., 123)
- Name: Any name

---

## 7. Interview Questions This Document Answers

1. **"Walk me through the ISO 8583 authorization flow at byte level"**
   → Section 3.1: Build 0100 with bitmap, pack fields, send TCP, receive 0110, check field 39.

2. **"What happens if the bank doesn't respond?"**
   → Section 5.1: Wait 5s timeout → send 0400 reversal → tell customer to retry.

3. **"How do you prevent double charging on timeout?"**
   → Always send reversal (0400) after timeout. Even if bank didn't process original, reversal is harmless.

4. **"How do you link a reversal to the original transaction?"**
   → Field 37 (RRN) is the same in both original and reversal messages.

5. **"What test cards do you use?"**
   → Section 6: Specific card numbers trigger specific behaviors.

---

## Next Step

→ Continue to **`phase2-part6-event-and-message-contracts.md`**
