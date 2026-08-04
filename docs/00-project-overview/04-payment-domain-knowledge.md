# PayFlow — Payment Domain Knowledge

**Document Version:** 1.0  
**Last Updated:** August 2026  

---

## Overview

Before building a payment gateway, you need to understand **how payments work** in the real world. This document explains the business concepts behind digital payments.

---

## 1. Key Players in Payment Ecosystem

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Payment Ecosystem                                     │
│                                                                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │ Customer │───▶│ Merchant │───▶│Payment Gateway│───▶│    Acquirer      │  │
│  │ (Buyer)  │    │ (Seller) │    │  (PayFlow)    │    │  (Bank Partner)  │  │
│  └──────────┘    └──────────┘    └──────────────┘    └────────┬─────────┘  │
│       │                                                         │            │
│       │                                                         ▼            │
│       │                                               ┌─────────────────┐   │
│       │                                               │  Card Network   │   │
│       │                                               │ (Visa/MC/RuPay) │   │
│       │                                               └────────┬────────┘   │
│       │                                                         │            │
│       │                                                         ▼            │
│       │                                               ┌─────────────────┐   │
│       └──────────────────────────────────────────────│   Issuing Bank  │   │
│                    (Customer's Bank)                   └─────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Players Explained:**

| Player | Role | Example |
|--------|------|---------|
| **Customer** | Person making payment | You buying on Amazon |
| **Merchant** | Business accepting payment | Amazon |
| **Payment Gateway** | Routes payments to banks | PayFlow, Razorpay, Stripe |
| **Acquirer** | Bank that works with merchants | HDFC Bank, ICICI Bank |
| **Card Network** | Routes between banks | Visa, Mastercard, RuPay |
| **Issuing Bank** | Customer's bank | Your SBI/HDFC card |

---

## 2. Payment Lifecycle

### 2.1 Authorization vs Capture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Two-Step Payment Flow                                     │
│                                                                              │
│  AUTHORIZATION (Step 1)                                                      │
│  ─────────────────────                                                       │
│  "Can this customer pay ₹5000?"                                             │
│                                                                              │
│  • Checks if card is valid                                                  │
│  • Checks if customer has ₹5000 available                                   │
│  • BLOCKS ₹5000 (customer can't spend it elsewhere)                         │
│  • Does NOT move money yet                                                   │
│                                                                              │
│  Result: Authorization Code (e.g., "AUTH123")                               │
│  Valid for: 7-30 days (depends on card network)                             │
│                                                                              │
│  ───────────────────────────────────────────────────────────────────────────│
│                                                                              │
│  CAPTURE (Step 2)                                                            │
│  ─────────────────                                                           │
│  "Now actually take the ₹5000"                                              │
│                                                                              │
│  • References the authorization code                                         │
│  • Actually moves money from customer to merchant                           │
│  • Can capture full or partial amount                                        │
│                                                                              │
│  Example: Hotel books ₹10,000 at check-in (auth)                            │
│           Captures ₹8,500 at checkout (actual bill)                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Why Two Steps?

| Scenario | Without Two-Step | With Two-Step |
|----------|------------------|---------------|
| Hotel booking | Charge ₹10,000 immediately, refund ₹1,500 later | Auth ₹10,000, capture only ₹8,500 |
| E-commerce | Charge even if item out of stock | Auth first, capture only when shipped |
| Car rental | Charge estimated amount | Auth estimate, capture actual usage |

---

## 3. Payment States

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Payment State Machine                                     │
│                                                                              │
│                         ┌─────────┐                                         │
│                         │ CREATED │                                         │
│                         └────┬────┘                                         │
│                              │                                               │
│              ┌───────────────┼───────────────┐                              │
│              │               │               │                              │
│              ▼               ▼               ▼                              │
│        ┌──────────┐   ┌───────────┐   ┌─────────┐                          │
│        │ EXPIRED  │   │PROCESSING │   │ FAILED  │                          │
│        │(timeout) │   └─────┬─────┘   │(error)  │                          │
│        └──────────┘         │         └─────────┘                          │
│                              │                                               │
│                              ▼                                               │
│                       ┌────────────┐                                        │
│                       │ AUTHORIZED │                                        │
│                       └──────┬─────┘                                        │
│                              │                                               │
│              ┌───────────────┼───────────────┐                              │
│              │               │               │                              │
│              ▼               ▼               ▼                              │
│        ┌──────────┐   ┌───────────┐   ┌─────────┐                          │
│        │  VOIDED  │   │ CAPTURED  │   │ EXPIRED │                          │
│        │(cancelled│   └─────┬─────┘   │(auth    │                          │
│        │ by merch)│         │         │expired) │                          │
│        └──────────┘         │         └─────────┘                          │
│                              │                                               │
│                              ▼                                               │
│                       ┌───────────┐                                         │
│                       │  SETTLED  │                                         │
│                       └─────┬─────┘                                         │
│                              │                                               │
│                              ▼                                               │
│                       ┌───────────┐                                         │
│                       │ REFUNDED  │                                         │
│                       │(full/part)│                                         │
│                       └───────────┘                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

**State Descriptions:**

| State | Description | Can Transition To |
|-------|-------------|-------------------|
| CREATED | Order created, awaiting payment | PROCESSING, EXPIRED |
| PROCESSING | Payment being processed | AUTHORIZED, FAILED |
| AUTHORIZED | Bank approved, money blocked | CAPTURED, VOIDED, EXPIRED |
| CAPTURED | Money transferred | SETTLED, REFUNDED |
| SETTLED | Money in merchant's account | REFUNDED |
| VOIDED | Authorization cancelled | (Terminal) |
| FAILED | Payment failed | (Terminal) |
| EXPIRED | Timed out | (Terminal) |
| REFUNDED | Money returned to customer | (Terminal) |

---

## 4. Settlement

### What is Settlement?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Settlement Process                                        │
│                                                                              │
│  Day 1: Payments captured                                                   │
│  ─────────────────────                                                       │
│  Payment 1: ₹1,000 (captured)                                               │
│  Payment 2: ₹2,500 (captured)                                               │
│  Payment 3: ₹500 (captured)                                                 │
│  Refund 1: -₹200                                                            │
│                                                                              │
│  Day 2 (T+1): Settlement calculated                                         │
│  ────────────────────────────────                                            │
│  Gross Amount: ₹4,000                                                       │
│  Refunds: -₹200                                                              │
│  MDR Fee (2%): -₹76                                                         │
│  GST on MDR (18%): -₹13.68                                                  │
│  ────────────────────────                                                    │
│  Net Settlement: ₹3,710.32                                                  │
│                                                                              │
│  Day 3 (T+2): Money transferred to merchant's bank account                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Settlement Schedules

| Schedule | Meaning | Use Case |
|----------|---------|----------|
| T+1 | Money next business day | Premium merchants |
| T+2 | Money in 2 business days | Standard merchants |
| T+3 | Money in 3 business days | New merchants |
| T+7 | Money in 7 days | High-risk merchants |

---

## 5. MDR (Merchant Discount Rate)

### What is MDR?

The fee merchants pay to accept digital payments.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Fee Distribution                                          │
│                                                                              │
│  Customer pays ₹100                                                         │
│  MDR: 2% = ₹2                                                               │
│                                                                              │
│  ₹2 split between:                                                          │
│  ┌────────────────────────────────────────────────────────────────────────┐│
│  │                                                                         ││
│  │   ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐   ││
│  │   │ Issuing   │    │   Card    │    │ Acquiring │    │  Payment  │   ││
│  │   │   Bank    │    │  Network  │    │   Bank    │    │  Gateway  │   ││
│  │   │           │    │           │    │           │    │           │   ││
│  │   │   ₹1.20   │    │   ₹0.10   │    │   ₹0.50   │    │   ₹0.20   │   ││
│  │   │   (60%)   │    │   (5%)    │    │   (25%)   │    │   (10%)   │   ││
│  │   └───────────┘    └───────────┘    └───────────┘    └───────────┘   ││
│  │                                                                         ││
│  └────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
│  Merchant receives: ₹98                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Typical MDR Rates (India)

| Payment Method | MDR Range | Notes |
|----------------|-----------|-------|
| Credit Card | 1.5% - 2.5% | Higher for international |
| Debit Card | 0.4% - 0.9% | RBI regulated |
| UPI | 0% | Zero MDR (govt. policy) |
| Net Banking | ₹5-15 flat | Per transaction |
| Wallets | 1% - 2% | Varies by wallet |

---

## 6. Payment Methods

### 6.1 Card Payments

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Card Payment Flow                                         │
│                                                                              │
│  1. Customer enters card details:                                           │
│     Card Number: 4111 1111 1111 1111                                        │
│     Expiry: 12/25                                                           │
│     CVV: 123                                                                 │
│                                                                              │
│  2. PayFlow validates format (Luhn check)                                   │
│                                                                              │
│  3. PayFlow builds ISO 8583 message                                         │
│                                                                              │
│  4. Send to bank via TCP                                                    │
│                                                                              │
│  5. Bank validates:                                                         │
│     - Card exists?                                                          │
│     - Not expired?                                                          │
│     - CVV correct?                                                          │
│     - Sufficient balance?                                                   │
│     - Not blocked?                                                          │
│                                                                              │
│  6. Bank returns approval/decline                                           │
│                                                                              │
│  7. If approved:                                                            │
│     - 3D Secure OTP (optional)                                             │
│     - Authorization complete                                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 UPI Payments

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    UPI Payment Flow                                          │
│                                                                              │
│  1. Customer enters VPA: user@upi                                           │
│                                                                              │
│  2. PayFlow sends collect request to NPCI                                   │
│                                                                              │
│  3. NPCI routes to customer's UPI app                                       │
│                                                                              │
│  4. Customer opens app, sees payment request:                               │
│     "Amazon wants ₹500"                                                     │
│                                                                              │
│  5. Customer enters UPI PIN                                                 │
│                                                                              │
│  6. Bank validates PIN, debits account                                      │
│                                                                              │
│  7. Success callback to PayFlow                                             │
│                                                                              │
│  Note: UPI is instant - no auth/capture separation                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Net Banking

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Net Banking Flow                                          │
│                                                                              │
│  1. Customer selects bank (HDFC, SBI, etc.)                                │
│                                                                              │
│  2. Redirected to bank's login page                                        │
│                                                                              │
│  3. Customer logs in with net banking credentials                          │
│                                                                              │
│  4. Bank shows payment confirmation                                         │
│                                                                              │
│  5. Customer approves                                                       │
│                                                                              │
│  6. Bank redirects back to merchant with success/failure                   │
│                                                                              │
│  Note: Redirect-based flow (customer leaves merchant site)                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Refunds

### Types of Refunds

| Type | Description | Timeline |
|------|-------------|----------|
| Full Refund | Return entire amount | 5-7 business days |
| Partial Refund | Return part of amount | 5-7 business days |
| Void | Cancel before capture | Instant release |
| Chargeback | Bank-initiated reversal | 45-120 days |

### Refund vs Void

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  VOID (Before Capture)                                                      │
│  ─────────────────────                                                       │
│  • Authorization exists, money blocked but not moved                        │
│  • Void releases the block immediately                                      │
│  • No fees charged                                                          │
│  • Customer sees: "Pending" disappears                                      │
│                                                                              │
│  REFUND (After Capture)                                                     │
│  ──────────────────────                                                      │
│  • Money already moved to merchant                                          │
│  • Refund creates reverse transaction                                       │
│  • MDR fees may or may not be refunded                                     │
│  • Takes 5-7 days to appear in customer account                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Idempotency

### Why Idempotency Matters

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WITHOUT Idempotency (DANGEROUS)                          │
│                                                                              │
│  1. Customer clicks "Pay ₹1000"                                            │
│  2. Request sent to PayFlow                                                 │
│  3. Network timeout (no response)                                           │
│  4. Customer clicks "Pay ₹1000" again                                      │
│  5. Another request sent                                                    │
│                                                                              │
│  Result: Customer charged ₹2000! 😱                                        │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                    WITH Idempotency (SAFE)                                  │
│                                                                              │
│  1. Customer clicks "Pay ₹1000"                                            │
│  2. Request sent with idempotency_key: "order_123_attempt_1"               │
│  3. PayFlow stores key in Redis: "order_123_attempt_1" → "processing"      │
│  4. Network timeout                                                         │
│  5. Customer clicks again (same idempotency_key)                           │
│  6. PayFlow checks Redis: key exists!                                       │
│  7. Returns previous result (no duplicate charge)                          │
│                                                                              │
│  Result: Customer charged ₹1000 only once ✓                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. Glossary

| Term | Meaning |
|------|---------|
| **Authorization** | Bank approval to charge a card |
| **Capture** | Actually moving money after auth |
| **Void** | Cancel an authorization |
| **Refund** | Return money after capture |
| **Settlement** | Batch transfer to merchant |
| **MDR** | Merchant Discount Rate (fee) |
| **Interchange** | Fee paid to issuing bank |
| **Acquirer** | Bank that processes for merchant |
| **Issuer** | Bank that issued customer's card |
| **BIN** | First 6 digits of card (identifies bank) |
| **PAN** | Full card number |
| **CVV** | 3-digit security code |
| **3DS** | 3D Secure (OTP verification) |
| **Chargeback** | Customer disputes, bank reverses |
| **PCI-DSS** | Card data security standard |
| **VPA** | Virtual Payment Address (UPI ID) |

---

## Next Steps

**Continue to:** [05-iso8583-protocol-guide.md](./05-iso8583-protocol-guide.md)

This will explain the ISO 8583 protocol — the binary message format used by banks worldwide.

---

**End of Payment Domain Knowledge**

*Next: [05-iso8583-protocol-guide.md](./05-iso8583-protocol-guide.md)*
