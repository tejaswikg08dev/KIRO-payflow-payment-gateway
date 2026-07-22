# Hands-On Guide — Phase 8 Part 2: Fee Calculation (MDR + GST)

## Goal

By the end of Part 2, you will have:
- FeeCalculator service with exact BigDecimal math
- Understanding of MDR (Merchant Discount Rate) — how payment gateways earn money
- Understanding of GST on MDR (government's cut)
- Unit test proving the math is correct
- Git commit

## Prerequisites

- Part 1 completed (settlement-service starts)
- Understanding of what MDR is (from Phase 1 Part 1 domain knowledge)

---

## How MDR Works (The Business Model)

```
WHEN A CUSTOMER PAYS ₹1,000 TO A MERCHANT:

The ₹1,000 is NOT all going to the merchant!
Payment gateway takes a fee for processing the payment.

THIS IS HOW STRIPE, RAZORPAY, AND ALL GATEWAYS MAKE MONEY:

Customer pays: ₹1,000
├── Gateway fee (MDR 2%): ₹20
│   This ₹20 is split among:
│   ├── Payment Gateway (us): ₹8 (0.8%)
│   ├── Card Network (Visa/MC): ₹4 (0.4%)
│   └── Issuing Bank (customer's bank): ₹8 (0.8%)
│
├── GST on MDR (18% of ₹20): ₹3.60
│   └── Goes to Indian Government (tax on financial services)
│
└── Merchant receives: ₹1,000 - ₹20 - ₹3.60 = ₹976.40

THAT'S HOW RAZORPAY MADE ₹2,000 CRORE REVENUE IN 2024:
├── Processed ₹10 lakh crore in payments
├── Average MDR: 0.02% (2 paise per ₹100) — varies by method
└── Revenue = ₹10,00,00,000 × 0.02% = ₹2,000 crore

FOR OUR PROJECT:
├── Default MDR: 2% (configurable per merchant)
├── GST rate: 18% (fixed by Indian government)
└── Settlement deducts both from merchant's payout
```

---

## MDR by Payment Method (India 2026)

```
CARD PAYMENTS:
├── Credit Card (Domestic): 1.5% - 2.5% (our default: 2%)
├── Credit Card (International): 3% - 3.5%
├── Debit Card (< ₹2000): 0.4% (RBI regulation)
├── Debit Card (> ₹2000): 0.8% - 0.9%
└── Corporate Card: 2.5% - 3%

UPI:
├── UPI (all amounts): 0% (zero MDR — government mandated!)
├── This is why merchants love UPI (no fee)
└── Gateways can't charge for UPI transactions

NET BANKING:
├── Flat fee: ₹3 - ₹10 per transaction (not percentage)
└── Same regardless of transaction amount

OUR DEFAULT:
├── All card payments: 2% MDR
├── UPI: 0% MDR (but we still process and settle)
└── Net Banking: ₹5 flat fee
```

---

## Step 2.1: FeeCalculator Service

**File:** `settlement-service/src/main/java/com/payflow/settlement/service/FeeCalculator.java`

```java
package com.payflow.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates settlement fees (MDR + GST).
 * 
 * ALL math uses BigDecimal (never double/float for money!).
 * ALL results rounded to 2 decimal places using HALF_UP (banker's rounding).
 * 
 * Formula:
 *   net_before_fee = gross_amount - refund_amount
 *   mdr_fee = net_before_fee × (mdr_percentage / 100)
 *   gst_on_fee = mdr_fee × (18 / 100)
 *   net_payout = net_before_fee - mdr_fee - gst_on_fee
 */
@Slf4j
@Service
public class FeeCalculator {

    // GST rate on MDR (Indian tax law: 18% on financial services)
    private static final BigDecimal GST_RATE = new BigDecimal("18.00");

    // Denominator for percentage calculation
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Calculate all fees for a settlement.
     * 
     * @param grossAmount Total captured payments (before any deductions)
     * @param refundAmount Total refunds (subtracted from gross)
     * @param mdrPercentage Merchant's MDR rate (e.g., 2.00 for 2%)
     * @return FeeResult with mdrFee, gstOnFee, netPayout
     * 
     * Example:
     *   calculate(₹50000, ₹5000, 2.00%)
     *   → mdrFee: ₹900, gstOnFee: ₹162, netPayout: ₹43,938
     */
    public FeeResult calculate(BigDecimal grossAmount, BigDecimal refundAmount,
                                BigDecimal mdrPercentage) {

        // Step 1: Net amount after refunds
        BigDecimal netBeforeFee = grossAmount.subtract(refundAmount);
        // ₹50,000 - ₹5,000 = ₹45,000
        // This is the amount we charge MDR on (not on refunded money)

        // Step 2: MDR fee
        BigDecimal mdrFee = netBeforeFee
                .multiply(mdrPercentage)      // ₹45,000 × 2.00
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);  // ÷ 100 = ₹900.00
        // RoundingMode.HALF_UP: 0.005 → 0.01 (always rounds 5 up)
        // Scale 2: Always 2 decimal places (paise precision)

        // Step 3: GST on MDR fee (18% of the fee, NOT of the transaction)
        BigDecimal gstOnFee = mdrFee
                .multiply(GST_RATE)           // ₹900 × 18.00
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);  // ÷ 100 = ₹162.00

        // Step 4: Net payout (what merchant actually receives)
        BigDecimal netPayout = netBeforeFee
                .subtract(mdrFee)             // ₹45,000 - ₹900
                .subtract(gstOnFee);          // - ₹162 = ₹43,838.00
        // Wait, let me recalculate:
        // ₹45,000 - ₹900 - ₹162 = ₹43,938.00 ← correct

        log.debug("Fee calculation: gross={}, refunds={}, MDR({}%)={}, GST={}, net={}",
                grossAmount, refundAmount, mdrPercentage, mdrFee, gstOnFee, netPayout);

        return new FeeResult(mdrFee, gstOnFee, netPayout);
    }

    /**
     * Immutable record holding fee calculation results.
     * Java record = class with final fields + auto-generated constructor/getters/equals/hashCode
     */
    public record FeeResult(
            BigDecimal mdrFee,      // Gateway's fee (2% of net)
            BigDecimal gstOnFee,    // Government's tax (18% of MDR fee)
            BigDecimal netPayout    // What merchant gets
    ) {}
}
```

---

## Step 2.2: Why BigDecimal (Critical for Money)

```java
// ══════════════════════════════════════════════════════════
// DEMONSTRATION: Why float/double is DANGEROUS for money
// ══════════════════════════════════════════════════════════

// WRONG (double):
double a = 0.1;
double b = 0.2;
System.out.println(a + b);
// OUTPUT: 0.30000000000000004   ← WRONG!!! Off by 0.00000000000000004

// Seems tiny? Over 1 million transactions:
// 1,000,000 × ₹0.00000000000000004 = ₹0.00000000004
// Not much... but what about this:

double amount = 1000.00;
double mdr = amount * 0.02;
System.out.println(mdr);
// OUTPUT: 20.000000000000004   ← Should be exactly 20.00!

// And this accumulates:
double total = 0;
for (int i = 0; i < 1000000; i++) {
    total += 0.01;  // Add 1 paisa, 1 million times
}
System.out.println(total);
// OUTPUT: 9999.999999999831   ← Should be 10000.00! Lost ₹0.000000000169!

// ══════════════════════════════════════════════════════════

// CORRECT (BigDecimal):
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
System.out.println(a.add(b));
// OUTPUT: 0.3   ← EXACTLY RIGHT!

BigDecimal amount = new BigDecimal("1000.00");
BigDecimal mdr = amount.multiply(new BigDecimal("0.02"));
System.out.println(mdr);
// OUTPUT: 20.00   ← EXACTLY RIGHT!

// RULE FOR PAYMENT SYSTEMS:
// ALWAYS use BigDecimal for ANY money calculation.
// ALWAYS use String constructor: new BigDecimal("0.1") not new BigDecimal(0.1)
// ALWAYS specify scale and RoundingMode in divide()
```

---

## Step 2.3: Unit Test for FeeCalculator

**Create file:** `settlement-service/src/test/java/com/payflow/settlement/service/FeeCalculatorTest.java`

```java
package com.payflow.settlement.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    @Test
    @DisplayName("Should calculate fees correctly for ₹50,000 gross with ₹5,000 refunds at 2% MDR")
    void calculate_StandardScenario() {
        // ARRANGE
        BigDecimal gross = new BigDecimal("50000.00");
        BigDecimal refunds = new BigDecimal("5000.00");
        BigDecimal mdrPercent = new BigDecimal("2.00");

        // ACT
        FeeCalculator.FeeResult result = calculator.calculate(gross, refunds, mdrPercent);

        // ASSERT
        // Net before fee: 50000 - 5000 = 45000
        // MDR: 45000 × 2% = 900
        // GST: 900 × 18% = 162
        // Net payout: 45000 - 900 - 162 = 43938
        assertThat(result.mdrFee()).isEqualByComparingTo("900.00");
        assertThat(result.gstOnFee()).isEqualByComparingTo("162.00");
        assertThat(result.netPayout()).isEqualByComparingTo("43938.00");
    }

    @Test
    @DisplayName("Should handle zero refunds")
    void calculate_NoRefunds() {
        FeeCalculator.FeeResult result = calculator.calculate(
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("2.00")
        );

        // MDR: 10000 × 2% = 200
        // GST: 200 × 18% = 36
        // Net: 10000 - 200 - 36 = 9764
        assertThat(result.mdrFee()).isEqualByComparingTo("200.00");
        assertThat(result.gstOnFee()).isEqualByComparingTo("36.00");
        assertThat(result.netPayout()).isEqualByComparingTo("9764.00");
    }

    @Test
    @DisplayName("Should handle UPI (0% MDR)")
    void calculate_ZeroMDR_UPI() {
        FeeCalculator.FeeResult result = calculator.calculate(
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO  // UPI = 0% MDR
        );

        assertThat(result.mdrFee()).isEqualByComparingTo("0.00");
        assertThat(result.gstOnFee()).isEqualByComparingTo("0.00");
        assertThat(result.netPayout()).isEqualByComparingTo("5000.00");
        // Merchant gets full amount for UPI! (no fee)
    }
}
```

**Run test:**
```cmd
cd settlement-service
mvn test -Dtest=FeeCalculatorTest
```

**Expected:** 3 tests pass ✓

---

## Step 2.4: Git Commit

```cmd
git add settlement-service/src/main/java/com/payflow/settlement/service/FeeCalculator.java
git add settlement-service/src/test/
git commit -m "Phase 8 Part 2: FeeCalculator (MDR + GST) with BigDecimal math + unit tests"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `service/FeeCalculator.java` | MDR + GST calculation with exact BigDecimal math |
| `test/FeeCalculatorTest.java` | Proves math is correct (3 scenarios) |

---

## Interview Notes

**Q: "How do you calculate settlement fees?"**
> "FeeCalculator takes gross amount, refund amount, and MDR percentage. It computes: net = gross - refunds, MDR = net × MDR%, GST = MDR × 18%. All math uses BigDecimal with HALF_UP rounding to 2 decimal places. I never use float/double for money — floating point errors accumulate over millions of transactions."

**Q: "Why is MDR important for the business?"**
> "MDR is how payment gateways make money. For every ₹100 processed, we keep ~₹2 (2% MDR). At scale of ₹1000 crore processed monthly, that's ₹20 crore revenue. Our system calculates this per transaction, per merchant, per settlement day — all with exact arithmetic."

---

## Next Step

→ Continue to **Phase 8 Part 3: Spring Batch Settlement Job**
