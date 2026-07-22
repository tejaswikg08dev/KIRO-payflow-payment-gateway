# Hands-On Guide — Phase 8 Part 4: Scheduler & Merchant Payout

## Goal

By the end of Part 4, you will have:
- Understanding of T+1, T+2 settlement schedules
- PayoutService that initiates bank transfers to merchants
- Simulated payout with UTR generation
- Settlement status flow: INITIATED → PROCESSED → COMPLETED
- Git commit

## Prerequisites

- Part 3 completed (batch job configured, scheduler working)

---

## Settlement Schedule Explained

```
WHEN DOES THE MERCHANT GET THEIR MONEY?

T+0 (Same day): Customer pays at 2PM → merchant gets money same evening
├── Used by: Premium merchants (pay extra fee for this privilege)
├── Risk: Higher (less time to detect fraud)
└── Example: Large retailers (Amazon, Flipkart)

T+1 (Next business day): Customer pays Monday → merchant gets money Tuesday
├── Used by: Large merchants with good track record
├── Most common for: E-commerce companies
└── Example: Standard Stripe settlement

T+2 (2 business days): Customer pays Monday → merchant gets money Wednesday
├── Used by: Standard merchants (OUR DEFAULT)
├── Why: Gives time to detect fraud, handle disputes
├── Most common in India for: New merchants
└── Example: New Razorpay merchants

T+3 (3 business days): Customer pays Monday → merchant gets money Thursday
├── Used by: New/small merchants (higher risk)
└── Why: Extra fraud protection window

WEEKLY: All payments for the week settled every Monday
├── Used by: Very small merchants
└── Why: Reduces payout processing overhead

"T" = Transaction date (when payment was CAPTURED)
"Business day" = Monday-Friday (excludes weekends and bank holidays)

OUR SYSTEM:
├── Default: T+2 for new merchants
├── Configurable per merchant (merchant_service stores schedule)
├── Settlement job runs daily at midnight
├── But actual PAYOUT is T+N days after capture date
└── Example: Payment captured July 19 → settled July 19 → payout July 21 (T+2)
```

---

## Step 4.1: PayoutService

**Create file:** `settlement-service/src/main/java/com/payflow/settlement/service/PayoutService.java`

```java
package com.payflow.settlement.service;

import com.payflow.settlement.model.Settlement;
import com.payflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Initiates bank transfers (payouts) to merchants.
 * 
 * In PRODUCTION:
 *   This would call a banking API (like NEFT/IMPS/UPI) to transfer money:
 *   POST https://bank-api.hdfc.com/transfers
 *   {from: "payflow_holding_account", to: "merchant_account", amount: 43938.00}
 *   Bank responds with UTR (Unique Transaction Reference).
 * 
 * In OUR PROJECT:
 *   We simulate the payout (generate fake UTR, mark as completed).
 *   The STRUCTURE is production-ready — just swap the bank call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final SettlementRepository settlementRepository;

    /**
     * Initiate payout for a settlement.
     * 
     * Steps:
     * 1. Validate settlement is in PROCESSED state
     * 2. Call bank API to transfer money (SIMULATED)
     * 3. Get UTR from bank
     * 4. Update settlement: status → COMPLETED, payout_utr = UTR
     */
    @Transactional
    public void initiatePayout(String settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new RuntimeException("Settlement not found: " + settlementId));

        if (settlement.getStatus() != Settlement.SettlementStatus.PROCESSED) {
            log.warn("Cannot payout settlement {} — status is {} (expected PROCESSED)",
                    settlementId, settlement.getStatus());
            return;
        }

        log.info("Initiating payout for settlement {}: ₹{} to merchant {}",
                settlementId, settlement.getNetAmount(), settlement.getMerchantId());

        // ===== SIMULATE BANK TRANSFER =====
        // In production: call NEFT/IMPS API here
        // For demo: generate fake UTR and mark complete
        String utr = generateUtr();
        
        // Simulate processing time (bank takes 1-2 seconds)
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // Update settlement with payout details
        settlement.setStatus(Settlement.SettlementStatus.COMPLETED);
        settlement.setPayoutUtr(utr);
        settlement.setProcessedAt(Instant.now());
        settlementRepository.save(settlement);

        log.info("Payout COMPLETED for settlement {}: UTR={}, amount=₹{}",
                settlementId, utr, settlement.getNetAmount());

        // TODO: Publish "settlement.processed" event to SQS
        // → webhook-service delivers to merchant
        // → notification-service sends email
    }

    /**
     * Generate a UTR (Unique Transaction Reference).
     * 
     * Real UTR format: HDFC + date + sequence
     * Example: "HDFC2026072000456"
     * 
     * For simulation: bank prefix + random
     */
    private String generateUtr() {
        return "HDFC" + System.currentTimeMillis() % 10000000000L;
        // Example: "HDFC2026072000456"
    }
}
```

---

## Step 4.2: Settlement Processing Flow

```java
// In SettlementService.processYesterdaysPayments():

public void processYesterdaysPayments() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    
    // 1. Get captured payments grouped by merchant (from payment-service)
    // (In real implementation: Feign call to payment-service)
    // For demo: simulate with test data
    
    Map<String, List<PaymentSummary>> byMerchant = getPaymentsByMerchant(yesterday);
    
    for (Map.Entry<String, List<PaymentSummary>> entry : byMerchant.entrySet()) {
        String merchantId = entry.getKey();
        List<PaymentSummary> payments = entry.getValue();
        
        // 2. Calculate totals
        BigDecimal gross = payments.stream()
                .map(PaymentSummary::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal refunds = getRefundsForMerchant(merchantId, yesterday);
        BigDecimal mdrPercent = getMerchantMdrRate(merchantId);
        
        // 3. Create settlement
        Settlement settlement = createSettlement(
                merchantId, yesterday, gross, refunds, mdrPercent,
                payments.size(), /* refund count */);
        
        // 4. Initiate payout
        payoutService.initiatePayout(settlement.getId());
    }
}
```

---

## Step 4.3: Verify

```cmd
# Start settlement service
cd settlement-service && mvn spring-boot:run

# Manually trigger settlement
curl -X POST http://localhost:8085/internal/trigger

# Check settlements created
curl http://localhost:8085/v1/settlements -H "X-Merchant-Id: merch_test"
```

**Expected:** Settlement record with status COMPLETED and UTR number.

---

## Step 4.4: Git Commit

```cmd
git add settlement-service/src/main/java/com/payflow/settlement/service/PayoutService.java
git commit -m "Phase 8 Part 4: PayoutService + settlement schedule explanation"
```

---

## Interview Notes

**Q: "What is T+2 settlement?"**
> "T+2 means the merchant receives their money 2 business days after payment capture. If a customer pays on Monday and merchant captures Monday, the settlement runs Monday night, and payout reaches merchant's bank on Wednesday. This delay gives us time to detect fraud and handle disputes before releasing money."

**Q: "How do you handle failed payouts?"**
> "If the bank API returns an error, settlement stays in PROCESSED state (not COMPLETED). A retry scheduler picks up failed payouts every hour. After 3 failures, it alerts the operations team. The merchant sees 'payout pending' on their dashboard."

---

## Next Step

→ Continue to **Phase 8 Part 5: Reconciliation & Reports**
