# Hands-On Guide — Phase 8 Part 6: Swagger & Testing

## Goal

By the end of Part 6, you will have:
- All settlement endpoints documented in Swagger UI
- End-to-end test: capture payment → trigger settlement → verify amounts
- Phase 8 COMPLETE
- Git commit

## Prerequisites

- Parts 1-5 completed (all settlement code working)
- Payment-service running (need captured payments to settle)

---

## Step 6.1: Settlement API Endpoints

**Swagger UI:** http://localhost:8085/swagger-ui.html

```
Settlements:
  GET  /v1/settlements                      List settlements for merchant
  GET  /v1/settlements/{id}                 Get settlement detail
  GET  /v1/settlements/{id}/report          Get transaction breakdown
  POST /internal/trigger                    Manually trigger settlement (admin)
```

---

## Step 6.2: Complete Controller

```java
@RestController
@RequestMapping("/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "View settlement records and download reports")
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementScheduler settlementScheduler;

    @GetMapping
    @Operation(summary = "List settlements for a merchant",
            description = "Returns all settlements, newest first. Shows gross, fee, net amounts.")
    public ResponseEntity<ApiResponse<List<Settlement>>> listSettlements(
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId) {
        String resolved = merchantId != null ? merchantId : "merch_default";
        List<Settlement> settlements = settlementService.getSettlementsForMerchant(resolved);
        return ResponseEntity.ok(ApiResponse.success(settlements));
    }

    @PostMapping("/internal/trigger")
    @Operation(summary = "Manually trigger daily settlement (admin/testing)",
            description = "Runs the same job that normally executes at midnight. "
                + "Useful for testing without waiting until midnight.")
    public ResponseEntity<ApiResponse<String>> triggerSettlement() {
        settlementScheduler.runDailySettlement();
        return ResponseEntity.ok(ApiResponse.success("Settlement triggered successfully"));
    }
}
```

---

## Step 6.3: End-to-End Test

```cmd
# 1. Make sure payment-service has a CAPTURED payment:
curl -X POST http://localhost:8083/v1/payments ^
  -H "Content-Type: application/json" ^
  -H "Idempotency-Key: settle_test_001" ^
  -H "X-Merchant-Id: merch_test" ^
  -d "{\"orderId\":\"ord_settle_test\",\"amount\":10000.00,\"method\":\"card\",\"card\":{\"number\":\"4111111111111111\",\"expiryMonth\":12,\"expiryYear\":2028,\"cvv\":\"123\"}}"

# 2. Capture it:
curl -X POST http://localhost:8083/v1/payments/PAY_ID/capture ^
  -H "Content-Type: application/json" ^
  -d "{\"amount\":10000.00}"

# 3. Trigger settlement:
curl -X POST http://localhost:8085/internal/trigger

# 4. Check settlement was created:
curl http://localhost:8085/v1/settlements -H "X-Merchant-Id: merch_test"
```

**Expected:** Settlement with:
- gross_amount: 10000.00
- fee_amount: 200.00 (2% MDR)
- gst_on_fee: 36.00 (18% of 200)
- net_amount: 9764.00 (10000 - 200 - 36)
- status: COMPLETED
- payout_utr: "HDFC..."

---

## Phase 8 COMPLETE! 🎉

| Part | What Was Built |
|------|---------------|
| Part 1 | Project setup, Settlement entity, repository, Flyway |
| Part 2 | FeeCalculator (MDR + GST, exact BigDecimal math) + unit test |
| Part 3 | Spring Batch Job config + Scheduler (midnight cron) |
| Part 4 | PayoutService (bank transfer simulation + UTR) |
| Part 5 | Reconciliation concept + settlement report endpoint |
| Part 6 | Swagger UI + end-to-end test |

**Settlement flow is complete:**
Captured payments → midnight batch → group by merchant → calculate fees → create settlement → initiate payout → mark COMPLETED

---

## Interview Notes

**Q: "Describe your settlement process"**
> "A scheduled Spring Batch job runs at midnight. It fetches all captured payments from yesterday, groups by merchant, calculates MDR (2%) + GST (18% on MDR) using BigDecimal for exact math. Creates a settlement record with net payout amount, initiates bank transfer, and marks payments as settled. The entire flow is idempotent (unique constraint on merchant+date prevents double settlement), restartable (Spring Batch metadata), and monitored (CloudWatch metrics on processing time and failure count)."

---

## Next Step

→ Move to **Phase 9: Webhook Service** (already documented in earlier sessions)

Or if following sequentially, proceed to Phase 9 Part 1.
