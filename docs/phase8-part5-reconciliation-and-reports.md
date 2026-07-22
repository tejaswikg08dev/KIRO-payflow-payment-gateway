# Hands-On Guide — Phase 8 Part 5: Reconciliation & Settlement Reports

## Goal

By the end of Part 5, you will have:
- Understanding of reconciliation (matching our records with bank records)
- Settlement report generation (what merchants download)
- Report endpoint (GET /v1/settlements/{id}/report)
- Git commit

## Prerequisites

- Part 4 completed (PayoutService working)

---

## What Is Reconciliation?

```
PROBLEM: How do we know our records match reality?

OUR SYSTEM SAYS:                    BANK'S RECORDS SAY:
├── Payment A: ₹5000 captured      ├── TXN 001: ₹5000 ✓ MATCH
├── Payment B: ₹3000 captured      ├── TXN 002: ₹3000 ✓ MATCH
├── Payment C: ₹2000 captured      ├── TXN 003: ₹2500 ✗ MISMATCH! (₹500 difference)
├── Payment D: ₹1000 captured      ├── (not found) ✗ MISSING FROM BANK!
└── (nothing)                       └── TXN 005: ₹800 ✗ BANK HAS EXTRA RECORD!

RECONCILIATION = Line-by-line comparison between our DB and bank's report.

WHY MISMATCHES HAPPEN:
├── Network timeout: We think declined, bank actually approved (held money)
├── Partial response: Bank processed but response got lost
├── Duplicate: Bank processed twice (rare but happens)
├── Reversal not applied: We sent reversal, bank didn't process it
└── Timing: Our midnight cutoff vs bank's midnight cutoff differ

WHAT WE DO WITH MISMATCHES:
├── Auto-resolve: Amount difference < ₹1 → rounding error → ignore
├── Alert ops: Amount difference > ₹1 → investigate
├── Bank has extra: Maybe our reversal failed → send reversal again
├── We have extra: Maybe bank declined but we didn't get response → check bank
└── Daily reconciliation report → ops team reviews every morning

FOR OUR PROJECT:
└── We simulate reconciliation (bank simulator = always matches)
└── But the STRUCTURE and PROCESS is production-ready
```

---

## Step 5.1: Settlement Report (What Merchants See)

```
When merchant opens their settlement page on dashboard, they see:

┌─────────────────────────────────────────────────────────────────────────┐
│ Settlement: July 19, 2026                    Status: ✅ COMPLETED       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Summary:                                                                │
│  ├── Total transactions: 15                                              │
│  ├── Gross amount: ₹50,000.00                                           │
│  ├── Refunds (2): -₹5,000.00                                           │
│  ├── Net before fee: ₹45,000.00                                        │
│  ├── MDR fee (2%): -₹900.00                                            │
│  ├── GST on MDR (18%): -₹162.00                                        │
│  ├── Net payout: ₹43,938.00                                            │
│  └── Payout UTR: HDFC2026072000456                                      │
│                                                                          │
│  Transactions:                                                           │
│  ┌──────────┬──────────────┬────────┬──────────┬────────┐              │
│  │ Time     │ Payment ID   │ Method │ Amount   │ Fee    │              │
│  ├──────────┼──────────────┼────────┼──────────┼────────┤              │
│  │ 09:15 AM │ pay_abc123   │ Card   │ ₹5,000   │ ₹100   │              │
│  │ 10:30 AM │ pay_def456   │ UPI    │ ₹1,200   │ ₹0     │              │
│  │ 11:45 AM │ pay_ghi789   │ Card   │ ₹15,000  │ ₹300   │              │
│  │ ...      │ ...          │ ...    │ ...      │ ...    │              │
│  └──────────┴──────────────┴────────┴──────────┴────────┘              │
│                                                                          │
│  [Download CSV] [Download PDF]                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Step 5.2: Report Endpoint

```java
// In SettlementController:

@GetMapping("/{settlementId}/report")
@Operation(summary = "Get settlement report (transaction breakdown)")
public ResponseEntity<ApiResponse<SettlementReport>> getReport(
        @PathVariable String settlementId) {
    SettlementReport report = settlementService.generateReport(settlementId);
    return ResponseEntity.ok(ApiResponse.success(report));
}

// SettlementReport DTO:
@Data @Builder
public class SettlementReport {
    private String settlementId;
    private LocalDate date;
    private String merchantName;
    private BigDecimal grossAmount;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private BigDecimal gstOnFee;
    private BigDecimal netPayout;
    private String payoutUtr;
    private String status;
    private int totalTransactions;
    private int totalRefunds;
    private List<TransactionLine> transactions;
    
    @Data @Builder
    public static class TransactionLine {
        private String paymentId;
        private String method;
        private BigDecimal amount;
        private BigDecimal fee;
        private Instant capturedAt;
    }
}
```

---

## Step 5.3: CSV Export (Future Enhancement)

```java
// GET /v1/settlements/{id}/report?format=csv
// Returns: Content-Type: text/csv

// CSV format:
// Payment ID, Method, Amount, Fee, Captured At
// pay_abc123, card, 5000.00, 100.00, 2026-07-19T09:15:00Z
// pay_def456, upi, 1200.00, 0.00, 2026-07-19T10:30:00Z
```

---

## Step 5.4: Git Commit

```cmd
git commit -m "Phase 8 Part 5: Reconciliation concept + settlement report endpoint"
```

---

## Interview Notes

**Q: "What is reconciliation?"**
> "Comparing our payment records with the bank's records to find discrepancies. We run daily reconciliation after settlement to verify: every payment we settled matches what the bank actually processed. Mismatches are flagged for investigation — could be timeout issues, lost reversals, or duplicate processing."

**Q: "What reports do merchants get?"**
> "Each settlement includes a breakdown: gross amount, refunds, MDR fee, GST, net payout, and individual transaction lines. Available via API (JSON), downloadable as CSV (for accounting software), and viewable on the merchant dashboard."

---

## Next Step

→ Continue to **Phase 8 Part 6: Swagger & Testing**
