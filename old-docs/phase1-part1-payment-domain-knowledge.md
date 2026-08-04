# Phase 1 — Part 1: Payment Domain Knowledge

> Read this completely before writing any code. This is the foundation knowledge
> that every payment engineer has. Interviewers will test these concepts.

---

## 1. How Does Online Payment Work?

### The Big Picture — What Happens When You Click "Pay"

When you buy something on Amazon or Swiggy and click "Pay ₹500", here's what happens
in the next 2-3 seconds behind the scenes:

```
Step 1: You (Customer) click "Pay ₹500" on Amazon
         │
         ▼
Step 2: Amazon (Merchant) sends payment request to Razorpay (Payment Gateway)
         │
         ▼
Step 3: Razorpay routes the request to HDFC Bank (Acquirer — merchant's bank)
         │
         ▼
Step 4: HDFC Bank sends it to Visa Network (Card Network)
         │
         ▼
Step 5: Visa routes it to SBI (Issuer — your bank, who issued your card)
         │
         ▼
Step 6: SBI checks: Does this customer have ₹500? Is the card valid? Not blocked?
         │
         ▼
Step 7: SBI says "APPROVED" (or "DECLINED") — travels back the same path
         │
         ▼
Step 8: You see "Payment Successful" on Amazon
```

**Total time:** 2-5 seconds for all of this.

---

### The Key Players (Entities)

```
┌──────────────┐   ┌──────────────┐   ┌─────────────────────┐
│   CUSTOMER   │   │   MERCHANT   │   │   PAYMENT GATEWAY   │
│  (Cardholder)│   │   (Seller)   │   │  (Stripe/Razorpay)  │
│              │   │              │   │                     │
│ Has a card   │   │ Sells goods  │   │ Processes payment   │
│ Wants to buy │   │ Wants money  │   │ Takes a fee (MDR)   │
└──────┬───────┘   └──────┬───────┘   └──────────┬──────────┘
       │                   │                      │
       │ pays              │ integrates API       │ routes to bank
       │                   │                      │
       ▼                   ▼                      ▼
┌──────────────┐   ┌──────────────┐   ┌─────────────────────┐
│   ISSUER     │   │   ACQUIRER   │   │    CARD NETWORK     │
│ (Your Bank)  │   │ (Merchant's  │   │   (Visa/Mastercard) │
│              │   │   Bank)      │   │                     │
│ Issued your  │   │ Receives     │   │ Routes between      │
│ card, holds  │   │ money for    │   │ issuer & acquirer   │
│ your money   │   │ merchant     │   │ Takes a fee         │
└──────────────┘   └──────────────┘   └─────────────────────┘
```

**Detailed explanation of each player:**

| Player | Full Name | What They Do | Example |
|--------|-----------|-------------|---------|
| **Customer** | Cardholder | Person making the purchase | You buying a phone |
| **Merchant** | Business/Seller | Accepts payment for goods/services | Amazon, Swiggy, Zomato |
| **Payment Gateway** | Payment Processor | Software that connects merchant to banking network | Stripe, Razorpay, PayU, Adyen, **PayFlow (us!)** |
| **Acquirer** | Acquiring Bank | Merchant's bank — receives money on merchant's behalf | HDFC Bank, ICICI (for the merchant) |
| **Card Network** | Card Scheme | Network that routes transactions between banks | Visa, Mastercard, RuPay, American Express |
| **Issuer** | Issuing Bank | Customer's bank — issued the card, holds the money | SBI, Axis Bank (for the customer) |

---

### Where Do We Fit?

We are building the **Payment Gateway** — the middleman between merchants and banks.

```
Amazon (Merchant) doesn't talk to banks directly.
Instead: Amazon → Razorpay (Gateway) → Banks

Why? Because:
├── Banks speak ISO 8583 (complex binary protocol)
├── There are 100+ banks — each needs separate integration
├── PCI-DSS compliance is expensive (₹50L+/year)
├── Fraud detection requires specialized systems
└── Settlement/reconciliation is complex

So merchants pay us (the gateway) 2% to handle all this complexity.
```

---

## 2. Payment Methods — How Each One Works

### 2.1 Credit/Debit Card Payment

```
Customer Journey:
1. Customer enters card number, expiry date, CVV on checkout page
2. Clicks "Pay"
3. Gets OTP on phone (3D Secure verification)
4. Enters OTP
5. Sees "Payment Successful"

Behind the Scenes:
1. Card details → Payment Gateway (us)
2. We validate: Is card number valid? (Luhn algorithm) Is it expired?
3. We build ISO 8583 message → send to bank via TCP
4. Bank checks balance, blocks amount → responds "Approved"
5. We trigger 3D Secure (OTP) for additional verification
6. Customer enters OTP → we confirm with bank
7. Payment is AUTHORIZED (money held, not yet transferred)
```

**Card Number Anatomy:**
```
4 1 1 1  1 1 1 1  1 1 1 1  1 1 1 1
│ └──────────────────────────────┼──── Account Number
│                                └──── Check Digit (Luhn)
└──── BIN (Bank Identification Number)
      4xxx = Visa
      5xxx = Mastercard
      6xxx = RuPay
      3xxx = American Express
```

**Luhn Algorithm (Card number validation):**
```
A quick check to see if a card number is even possible (not a random number):
1. Double every second digit from right
2. If doubled digit > 9, subtract 9
3. Sum all digits
4. If sum % 10 == 0 → VALID

Example: 4111 1111 1111 1111
This always passes Luhn → commonly used test card number
```

---

### 2.2 UPI Payment (India-Specific)

```
Customer Journey:
1. Customer enters VPA (like name@upi or 9876543210@ybl)
2. Clicks "Pay"
3. Gets notification on UPI app (Google Pay, PhonePe)
4. Enters UPI PIN on their phone
5. Payment is completed instantly

Behind the Scenes:
1. Merchant sends collect request → Payment Gateway → NPCI
2. NPCI routes to customer's UPI app
3. Customer approves with PIN
4. Money moves instantly (bank-to-bank)
5. NPCI confirms to gateway → gateway confirms to merchant
```

**UPI Architecture:**
```
┌─────────────┐       ┌──────────┐       ┌──────────────┐
│ Google Pay   │       │   NPCI   │       │ Customer's   │
│ PhonePe     │◄─────►│ (Switch) │◄─────►│ Bank (SBI)   │
│ Paytm       │       │          │       │              │
└─────────────┘       └──────────┘       └──────────────┘
                           ▲
                           │
                    ┌──────┴──────┐
                    │  Merchant's │
                    │  Bank (HDFC)│
                    └─────────────┘
```

**VPA (Virtual Payment Address):**
- Format: `username@bankhandle`
- Examples: `john@okicici`, `9876543210@ybl`, `shop@paytm`
- It's like an email address but for money

---

### 2.3 Net Banking Payment

```
Customer Journey:
1. Customer selects "Net Banking" → chooses their bank (SBI, HDFC, etc.)
2. Gets redirected to their bank's website
3. Logs in with internet banking credentials
4. Sees payment details → clicks "Confirm"
5. Gets redirected back to merchant with success/failure

Behind the Scenes:
1. Gateway generates a redirect URL to the bank
2. Customer authenticates directly with their bank
3. Bank processes payment, generates a reference number
4. Bank redirects back to gateway's callback URL
5. Gateway verifies the callback (signature check)
6. Payment confirmed
```

**Flow diagram:**
```
Merchant Site → Gateway Checkout → Bank Website → Back to Gateway → Merchant Site
                (select bank)      (login+confirm)   (verify)         (success!)
```

---

### 2.4 Wallet Payment

```
Customer Journey:
1. Customer has pre-loaded money in a wallet (Paytm, Amazon Pay)
2. Selects "Pay with Wallet"
3. Enters wallet PIN/OTP
4. Money deducted from wallet balance
5. Payment successful

Behind the Scenes:
1. Gateway calls wallet provider's API
2. Wallet checks balance
3. Deducts amount (internal ledger operation)
4. Returns success
```

---

## 3. Payment Lifecycle — States & Transitions

### 3.1 The Complete State Machine

Every payment in our system goes through specific states. Think of it like a package delivery — ordered → shipped → delivered.

```
                              ┌──────────────────┐
                              │                  │
                              │     CREATED      │ ← Merchant calls POST /orders
                              │                  │
                              └────────┬─────────┘
                                       │
                                       │ Customer submits payment details
                                       │ (card/UPI/net banking)
                                       ▼
                    ┌──────────────────────────────────────────┐
                    │                                          │
          ┌─────────┤            PROCESSING                    │
          │         │                                          │
          │         └────────────────────┬─────────────────────┘
          │                              │
          │ Bank declines               │ Bank approves (holds money)
          │                              │
          ▼                              ▼
┌──────────────────┐          ┌──────────────────┐
│                  │          │                  │
│     FAILED       │          │   AUTHORIZED     │ ← Money is HELD (not moved)
│                  │          │                  │
└──────────────────┘          └───┬──────────┬───┘
                                  │          │
                    Merchant calls │          │ Authorization expires
                    /capture       │          │ (typically 7 days)
                                  │          │
                                  ▼          ▼
                    ┌──────────────────┐  ┌──────────────────┐
                    │                  │  │                  │
                    │    CAPTURED      │  │    EXPIRED       │
                    │                  │  │                  │
                    └───┬──────────┬───┘  └──────────────────┘
                        │          │
          Merchant calls│          │ End-of-day batch
          /refund       │          │ settlement process
                        │          │
                        ▼          ▼
          ┌──────────────────┐  ┌──────────────────┐
          │                  │  │                  │
          │   REFUNDED       │  │    SETTLED       │ ← Money in merchant's account
          │  (full/partial)  │  │                  │
          └──────────────────┘  └──────────────────┘
```

**Additionally, from AUTHORIZED state:**
```
AUTHORIZED ──── merchant calls /void ────► VOIDED (hold released, money back to customer)
```

---

### 3.2 Each State Explained

| State | What Happened | Money Status | Who Triggered |
|-------|-------------|-------------|---------------|
| **CREATED** | Merchant created a payment order | No money involved yet | Merchant (POST /orders) |
| **PROCESSING** | Customer submitted payment, we're talking to bank | Being checked | System (automatic) |
| **AUTHORIZED** | Bank approved, money is HELD on customer's card | Held (blocked) but not moved | Bank response |
| **CAPTURED** | Merchant confirmed, money is DEDUCTED from customer | Deducted from customer, in transit | Merchant (POST /capture) |
| **SETTLED** | Money reached merchant's bank account | In merchant's account | Settlement batch job |
| **VOIDED** | Merchant cancelled before capture, hold released | Released back to customer | Merchant (POST /void) |
| **REFUNDED** | Money returned to customer after capture | Returned to customer | Merchant (POST /refund) |
| **FAILED** | Bank declined or error occurred | Nothing happened | Bank response or timeout |
| **EXPIRED** | Customer didn't complete payment in time (30 min) | Nothing happened | System timeout |

---

### 3.3 Real-World State Examples

**Example 1 — E-commerce (Amazon):**
```
1. Customer clicks "Buy Now" for a ₹50,000 laptop
   → CREATED

2. Customer enters card details and OTP
   → PROCESSING → AUTHORIZED (₹50,000 held on card)

3. Amazon confirms stock and ships the laptop (2 days later)
   → CAPTURED (₹50,000 deducted from customer)

4. End of day settlement
   → SETTLED (₹49,000 reaches Amazon's bank — ₹1,000 MDR fee)
```

**Example 2 — Hotel Booking (MakeMyTrip):**
```
1. Customer books hotel for ₹10,000
   → CREATED → AUTHORIZED

2. Customer cancels booking before check-in
   → VOIDED (₹10,000 hold released)
```

**Example 3 — Ride Hailing (Uber):**
```
1. Customer books ride, app authorizes ₹500
   → AUTHORIZED (₹500 held)

2. Ride completes, actual fare is ₹320
   → CAPTURED for ₹320 only (partial capture)
   → Remaining ₹180 hold released automatically
```

**Example 4 — Failed Payment:**
```
1. Customer tries to pay ₹5,000 but has only ₹2,000 in account
   → PROCESSING → FAILED (insufficient funds, response code 51)
```

---

## 4. Authorization vs Capture — Deep Dive

### 4.1 Why Two Steps?

In many real-world scenarios, the merchant can't charge immediately:

| Scenario | Why Can't Charge Immediately? |
|----------|------------------------------|
| Amazon | Don't know if item is in stock until warehouse confirms |
| Hotel | Guest might cancel before check-in |
| Car rental | Final amount depends on usage (fuel, damage, extra days) |
| Restaurant | Tip amount unknown at time of card swipe |
| Subscription | Trial period — charge only if customer doesn't cancel |

**Authorization** = "Can this customer pay this amount?"
**Capture** = "OK, actually charge them now."

---

### 4.2 Authorization Hold

When we authorize:
- Bank **blocks** the amount on customer's card
- Customer sees "pending" charge on statement
- Money is NOT transferred yet
- Hold typically expires in **7 days** (varies by bank)
- If we don't capture within 7 days → hold auto-releases

```
Customer's Card Limit: ₹1,00,000
Available after authorization of ₹30,000: ₹70,000
(₹30,000 is blocked but not spent)
```

---

### 4.3 Capture Types

| Type | What | Example |
|------|------|---------|
| **Full Capture** | Capture exact authorized amount | Amazon captures ₹50,000 for laptop |
| **Partial Capture** | Capture less than authorized | Uber authorized ₹500, captures ₹320 |
| **Multi-Capture** | Multiple captures against one auth | Hotel: ₹10,000 room + ₹2,000 minibar (not all gateways support) |

---

## 5. Settlement — How Merchants Get Paid

### 5.1 What Is Settlement?

Settlement is the process where:
1. Gateway collects all **captured** payments for a day
2. Groups them by merchant
3. Deducts gateway fee (MDR)
4. Transfers remaining amount to merchant's bank

```
Example — Settlement for "ABC Electronics" on July 19:

Captured Payments Today:
├── Payment 1: ₹5,000  (customer bought headphones)
├── Payment 2: ₹15,000 (customer bought laptop charger)
├── Payment 3: ₹2,000  (customer bought case)
├── Refund 1:  -₹5,000 (headphones returned)
└── Total: ₹17,000

Fee Calculation:
├── Gross amount: ₹17,000
├── MDR (2%): -₹340
├── GST on MDR (18%): -₹61.20
└── Net settlement: ₹16,598.80

Settlement Transfer:
├── Amount: ₹16,598.80
├── To: ABC Electronics, HDFC Bank, Acc: XXXX1234
├── Date: July 21 (T+2)
└── Reference: SETL_20260719_ABC
```

---

### 5.2 Settlement Schedule

| Term | Meaning | Who Uses It |
|------|---------|-------------|
| **T+0** | Same day settlement | Premium merchants (extra fee) |
| **T+1** | Next business day | Large merchants (Flipkart, Uber) |
| **T+2** | 2 business days after capture | Standard merchants |
| **T+3** | 3 business days | Small merchants, new accounts |
| **Weekly** | Once a week | Very small merchants |

**T = Transaction date (day of capture)**

---

### 5.3 Split Settlement (Marketplace)

For marketplaces (like Amazon Marketplace, Uber Eats):

```
Customer pays ₹1,000 for food on Uber Eats

Split:
├── Restaurant gets: ₹700 (70%)
├── Delivery partner gets: ₹100 (10%)
├── Uber Eats platform gets: ₹200 (20%)
└── Gateway fee deducted from each party proportionally
```

---

## 6. MDR — How Payment Gateways Make Money

### 6.1 Fee Breakdown

```
Customer pays ₹1,000 with Visa credit card:

Total MDR: 2.0% = ₹20

This ₹20 is split among:
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  ₹20 total MDR                                              │
│  ├── Interchange Fee (to Issuing Bank): ₹12 (1.2%)         │
│  ├── Network Fee (to Visa/Mastercard):  ₹2  (0.2%)         │
│  └── Acquirer Markup (to Gateway/Acquirer): ₹6  (0.6%)     │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Merchant receives: ₹1,000 - ₹20 = ₹980
```

### 6.2 Our Fee Structure (For This Project)

| Payment Method | Fee Type | Rate |
|---------------|----------|------|
| Credit Card (Domestic) | Percentage | 2.0% |
| Debit Card (Domestic) | Percentage | 0.8% |
| UPI | Percentage | 0% (free by regulation) |
| Net Banking | Fixed | ₹5 per transaction |
| International Card | Percentage | 3.5% |

### 6.3 GST on MDR

In India, 18% GST is charged on the MDR fee:
```
MDR: ₹20
GST on MDR: ₹20 × 18% = ₹3.60
Total deduction from merchant: ₹23.60
```

---

## 7. Idempotency — Preventing Double Charges

### 7.1 The Problem

```
Scenario:
1. Customer clicks "Pay ₹5,000"
2. Request goes to our server
3. We process it, charge the card
4. Response traveling back to customer
5. NETWORK TIMEOUT! Customer sees "error, try again"
6. Customer clicks "Pay ₹5,000" AGAIN
7. We process it AGAIN → Customer charged ₹10,000!! 💀
```

### 7.2 The Solution — Idempotency Key

```
How it works:

FIRST REQUEST:
POST /v1/payments
Headers: {
  "Idempotency-Key": "order_abc_attempt_1"     ← Unique key per payment attempt
}
Body: { "amount": 5000, "card": "4111..." }

Server:
1. Check Redis: Does key "order_abc_attempt_1" exist? → NO
2. Process payment → SUCCESS, payment_id = "pay_xyz"
3. Store in Redis: "order_abc_attempt_1" → { payment_id: "pay_xyz", status: "success" }
4. Return response: { payment_id: "pay_xyz" }

SECOND REQUEST (duplicate/retry):
POST /v1/payments
Headers: {
  "Idempotency-Key": "order_abc_attempt_1"     ← SAME key!
}
Body: { "amount": 5000, "card": "4111..." }

Server:
1. Check Redis: Does key "order_abc_attempt_1" exist? → YES!
2. Return CACHED response: { payment_id: "pay_xyz" }
3. Do NOT process payment again!

Result: Customer charged only once ✅
```

### 7.3 Idempotency Key Rules

| Rule | Why |
|------|-----|
| Key must be unique per payment attempt | Different payments need different keys |
| Key stored in Redis with 24-hour TTL | Don't keep forever, but long enough for retries |
| Key is per merchant | Different merchants can use same key (isolated) |
| If same key + different body → return error | Prevent misuse (changing amount on retry) |

---

## 8. Webhooks — Real-Time Event Notification

### 8.1 What Are Webhooks?

A webhook is when **we call the merchant's server** to notify them about events.

Instead of merchant asking us "is payment done?" every 5 seconds (polling),
we proactively TELL them when something happens.

### 8.2 Webhook Events We'll Send

| Event | When It Fires | Merchant Action |
|-------|--------------|-----------------|
| `payment.authorized` | Payment authorized by bank | Show "processing" to customer |
| `payment.captured` | Payment captured successfully | Fulfill order, ship product |
| `payment.failed` | Payment failed/declined | Show error, retry option |
| `refund.created` | Refund initiated | Update order status |
| `refund.completed` | Refund money returned | Notify customer "refund done" |
| `settlement.processed` | Settlement payout done | Update accounting |
| `dispute.created` | Customer disputed (chargeback) | Provide evidence |

### 8.3 Webhook Payload Format

```json
{
  "id": "evt_1234567890",
  "event": "payment.captured",
  "created_at": "2026-07-19T14:30:00Z",
  "data": {
    "payment_id": "pay_abc123",
    "order_id": "order_xyz789",
    "amount": 5000,
    "currency": "INR",
    "status": "captured",
    "method": "card",
    "card": {
      "last4": "1111",
      "network": "visa"
    },
    "merchant_id": "merch_001"
  }
}
```

### 8.4 Webhook Security (HMAC-SHA256)

Merchant needs to verify that the webhook is really from us (not a hacker):

```
We generate:
1. Take webhook body (JSON string)
2. Sign with merchant's webhook_secret using HMAC-SHA256
3. Put signature in header: X-PayFlow-Signature: "sha256=abc123..."

Merchant verifies:
1. Receive webhook
2. Take body, compute HMAC-SHA256 with their stored secret
3. Compare computed signature with header signature
4. If match → it's really from PayFlow ✅
5. If no match → reject (someone is faking webhooks!) ❌
```

### 8.5 Webhook Reliability — Retry Strategy

```
Attempt 1: Immediately after event
  └── If merchant returns 2xx → done ✅
  └── If 4xx/5xx or timeout → retry

Attempt 2: After 5 minutes
  └── If 2xx → done ✅
  └── If fail → retry

Attempt 3: After 30 minutes
Attempt 4: After 2 hours
Attempt 5: After 24 hours
  └── If still failing → move to Dead Letter Queue (DLQ)
  └── Merchant can manually retry from dashboard
```

---

## 9. Fraud Detection Basics

### 9.1 Common Fraud Types

| Type | How It Works | Our Defense |
|------|-------------|-------------|
| **Stolen Card** | Fraudster uses stolen card number | Velocity checks, 3D Secure |
| **Card Testing** | Bot tries 1000s of cards with ₹1 charges | Rate limiting, CAPTCHA |
| **Friendly Fraud** | Customer buys, then disputes "I didn't buy this" | Transaction logs, delivery proof |
| **Account Takeover** | Hacker gains access to merchant account | 2FA, IP monitoring |
| **BIN Attack** | Generate card numbers from a known BIN | Velocity per BIN, block BIN |

### 9.2 Our Fraud Detection Rules

```
For every transaction, calculate risk score (0-100):

BASE SCORE: 0 (start clean)

ADD POINTS FOR:
├── Amount > ₹50,000                    → +25
├── First transaction ever for this card → +15
├── New device (never seen before)       → +20
├── Transaction between 2AM-5AM          → +15
├── Different city than usual            → +10
├── >3 transactions in last 5 minutes    → +30
├── Card used in different country       → +35
├── Multiple failed attempts before this → +20
└── Merchant category is high-risk       → +10

DECISION:
├── Score 0-40:   LOW RISK    → AUTO APPROVE ✅
├── Score 41-70:  MEDIUM RISK → APPROVE + trigger 3DS (OTP) ⚠️
├── Score 71-90:  HIGH RISK   → SEND TO MANUAL REVIEW 🔍
└── Score 91-100: VERY HIGH   → AUTO DECLINE ❌
```

---

## 10. Key Payment Terms — Complete Glossary

### Card Related
| Term | Full Form | Meaning |
|------|-----------|---------|
| PAN | Primary Account Number | The 16-digit card number |
| CVV/CVC | Card Verification Value | 3-digit code on back of card |
| BIN | Bank Identification Number | First 6 digits of card (identifies issuing bank) |
| EMV | Europay-Mastercard-Visa | Chip card technology standard |
| POS | Point of Sale | Card swipe/tap machine at shops |
| NFC | Near Field Communication | Contactless "tap to pay" technology |
| Tokenization | - | Replacing real card number with a random token for security |

### Transaction Related
| Term | Full Form | Meaning |
|------|-----------|---------|
| STAN | System Trace Audit Number | 6-digit unique transaction identifier |
| RRN | Retrieval Reference Number | 12-digit reference for tracking transaction |
| ARN | Acquirer Reference Number | Used for settlement tracking |
| Auth Code | Authorization Code | 6-character code from bank confirming approval |
| 3D Secure | 3-Domain Secure | OTP verification for online card payments (Visa Secure, Mastercard SecureCode) |

### Business Related
| Term | Full Form | Meaning |
|------|-----------|---------|
| MDR | Merchant Discount Rate | Fee charged to merchant per transaction |
| TDR | Transaction Discount Rate | Same as MDR (different name) |
| Chargeback | - | Customer disputes charge, money pulled back from merchant |
| Settlement | - | Process of paying merchant their money |
| Reconciliation | - | Matching our records with bank's records |
| PCI-DSS | Payment Card Industry Data Security Standard | Security compliance rules for handling card data |
| KYC | Know Your Customer | Identity verification of merchant |

### India-Specific (UPI/Banking)
| Term | Full Form | Meaning |
|------|-----------|---------|
| UPI | Unified Payments Interface | India's instant payment system (by NPCI) |
| VPA | Virtual Payment Address | UPI ID like name@upi |
| NPCI | National Payments Corporation of India | Organization that runs UPI, RuPay, IMPS |
| IMPS | Immediate Payment Service | Instant interbank transfer |
| NEFT | National Electronic Funds Transfer | Batch-based bank transfer (every 30 min) |
| RTGS | Real Time Gross Settlement | High-value instant transfer (min ₹2 lakh) |
| NACH | National Automated Clearing House | For recurring debits (EMI, subscriptions) |
| RuPay | - | India's domestic card network (like Visa but Indian) |
| BBPS | Bharat Bill Payment System | For bill payments (electricity, phone) |

### Protocol Related
| Term | Full Form | Meaning |
|------|-----------|---------|
| ISO 8583 | - | International standard for financial messages |
| MTI | Message Type Indicator | 4-digit code telling message type (0100, 0200, etc.) |
| Bitmap | - | Binary field telling which data fields are present |
| TCP/IP | - | Network protocol used to send ISO 8583 messages |

---

## 11. How Payment Gateway Companies Really Work (Industry Insights)

### 11.1 Stripe's Architecture (Simplified)

```
External API (REST) → Internal Services → Bank Integrations (ISO 8583 / API)
       │                     │                      │
   Idempotent           State Machine          Multiple acquirers
   API Keys             Event Sourcing         Smart routing
   Versioning           Saga Pattern           Failover
   Rate Limiting        CQRS                   Retry logic
```

### 11.2 Razorpay's Architecture (India)

```
Merchant → API → Payment Orchestrator → Routing Engine → Bank/UPI/Wallet
                         │                     │
                    Fraud Engine          ISO 8583 (cards)
                    3DS Service          UPI Collect (NPCI)
                    Token Vault          Net Banking (redirect)
```

### 11.3 Scale Numbers (For Interview Context)

| Metric | Stripe | Razorpay | Context |
|--------|--------|----------|---------|
| Transactions/sec | ~10,000 | ~3,000 | Peak during sales |
| Latency requirement | <200ms | <300ms | Auth response time |
| Uptime SLA | 99.99% | 99.9% | Can't go down! |
| Data stored | Petabytes | Terabytes | Transaction logs |

---

## 12. Interview Questions This Knowledge Answers

After reading this document, you can confidently answer:

1. "Explain the payment flow from customer to bank"
2. "What's the difference between authorization and capture?"
3. "How do you prevent duplicate payments?" → Idempotency
4. "What is settlement and how does it work?"
5. "What is MDR? How does the payment gateway earn money?"
6. "How do webhooks work? How do you ensure reliability?"
7. "What fraud prevention techniques would you implement?"
8. "What is 3D Secure?"
9. "What is PCI-DSS and why does it matter?"
10. "Explain UPI architecture"
11. "What is ISO 8583?"
12. "What payment states does a transaction go through?"

---

## Next Step

→ Continue to **`phase1-part2-iso8583-protocol-deep-dive.md`**
