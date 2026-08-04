# Sprint 2, Part 05: Fee Plans (MDR Configuration)

**Duration:** 30 minutes  
**Prerequisites:** Part 04 completed  
**Goal:** Understand the existing MDR (Merchant Discount Rate) configuration

---

## 1. Learning Objectives

By the end of this part, you will:
- Understand what MDR (Merchant Discount Rate) is
- Review the existing fee configuration in the Merchant entity
- Understand how fees will be applied to payments (future sprints)

---

## 2. What is MDR?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    MERCHANT DISCOUNT RATE (MDR)                              │
│                                                                              │
│  MDR = The fee charged to merchants for processing payments                 │
│                                                                              │
│  Example:                                                                    │
│  ─────────                                                                  │
│  Customer pays:     ₹1,000                                                  │
│  MDR (2%):          ₹20                                                     │
│  GST on MDR (18%):  ₹3.60                                                   │
│  Merchant receives: ₹976.40                                                 │
│                                                                              │
│  Who pays MDR?                                                               │
│  ──────────────                                                             │
│  • Usually the MERCHANT (deducted from settlement)                          │
│  • Sometimes passed to CUSTOMER (shown in checkout)                         │
│  • PayFlow's revenue comes from MDR                                         │
│                                                                              │
│  Typical MDR rates in India:                                                 │
│  ────────────────────────────                                               │
│  • Credit cards: 1.5% - 2.5%                                               │
│  • Debit cards:  0.4% - 0.9%                                               │
│  • UPI:          0% (subsidized by govt)                                   │
│  • Net banking:  ₹5 - ₹15 flat                                             │
│                                                                              │
│  PayFlow default: 2.00%                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Existing MDR Configuration

### 3.1 Merchant Entity Fields

**File:** `merchant-service/src/main/java/com/payflow/merchant/model/Merchant.java`

```java
@Column(name = "mdr_percentage", nullable = false, precision = 5, scale = 2)
@Builder.Default
private BigDecimal mdrPercentage = new BigDecimal("2.00");  // 2.00%

@Column(name = "settlement_schedule", length = 10, nullable = false)
@Builder.Default
private String settlementSchedule = "T+2";  // Settlement in 2 business days
```

### 3.2 Database Column

```sql
-- From V1__create_merchant_tables.sql
CREATE TABLE merchant.merchants (
    ...
    mdr_percentage     DECIMAL(5,2) NOT NULL DEFAULT 2.00,
    settlement_schedule VARCHAR(10) NOT NULL DEFAULT 'T+2',
    ...
);
```

---

## 4. Fee Calculation Flow (Future Sprint)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FEE CALCULATION (Payment Service)                         │
│                                                                              │
│  Input:                                                                      │
│  ───────                                                                    │
│  Payment amount: ₹1,000.00                                                  │
│  Merchant MDR:   2.00%                                                      │
│  GST on MDR:     18.00%                                                     │
│                                                                              │
│  Calculation:                                                                │
│  ────────────                                                               │
│  MDR fee        = amount × mdr_percentage / 100                             │
│                 = 1000 × 2.00 / 100                                         │
│                 = ₹20.00                                                    │
│                                                                              │
│  GST on MDR     = mdr_fee × gst_rate / 100                                 │
│                 = 20 × 18 / 100                                             │
│                 = ₹3.60                                                     │
│                                                                              │
│  Total fee      = mdr_fee + gst                                             │
│                 = 20 + 3.60                                                 │
│                 = ₹23.60                                                    │
│                                                                              │
│  Net settlement = amount - total_fee                                        │
│                 = 1000 - 23.60                                              │
│                 = ₹976.40                                                   │
│                                                                              │
│  Output (stored in payment record):                                          │
│  ──────────────────────────────────                                         │
│  {                                                                           │
│    "amount": 1000.00,                                                       │
│    "mdr_fee": 20.00,                                                        │
│    "gst_fee": 3.60,                                                         │
│    "net_amount": 976.40                                                     │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Settlement Schedule Options

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SETTLEMENT SCHEDULES                                      │
│                                                                              │
│  T+0: Same day settlement (premium, higher fees)                            │
│  T+1: Next business day settlement                                          │
│  T+2: Two business days (default, standard fees)                            │
│  T+3: Three business days (lower fees)                                      │
│                                                                              │
│  Example:                                                                    │
│  ─────────                                                                  │
│  Payment on Monday 10 AM                                                    │
│  T+0: Settled Monday EOD                                                    │
│  T+1: Settled Tuesday EOD                                                   │
│  T+2: Settled Wednesday EOD                                                 │
│                                                                              │
│  Why T+2 is default?                                                         │
│  ────────────────────                                                       │
│  • Allows time for fraud detection                                          │
│  • Handles chargebacks before settlement                                    │
│  • Standard in Indian payment industry                                      │
│  • RBI guidelines recommend T+2 for new merchants                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Future Enhancements (Not in Sprint 2)

| Feature | Sprint | Description |
|---------|--------|-------------|
| MDR update endpoint | Sprint 4 | Allow merchants to view/request MDR changes |
| Tiered pricing | Sprint 5 | Lower MDR for high-volume merchants |
| Payment method fees | Sprint 5 | Different fees for UPI vs cards |
| Fee invoicing | Sprint 6 | Monthly fee statements |

---

## 7. Key Takeaways

| Concept | Remember |
|---------|----------|
| **MDR** | Fee percentage charged per transaction |
| **Default** | 2.00% MDR, T+2 settlement |
| **GST** | 18% charged on MDR (PayFlow's responsibility) |
| **Settlement** | Net amount = Gross - MDR - GST |

---

## 8. Next Steps

**Continue to:** [part-06-merchant-swagger-testing.md](./part-06-merchant-swagger-testing.md)

In the next part, you'll test all merchant service endpoints using Swagger.

---

**End of Sprint 2, Part 05**
