# Hands-On Guide — Phase 10 Part 3: Fraud Detection Rule Engine

## Goal
- Complete fraud rule engine with configurable rules
- Each rule contributes points to a risk score (0-100)
- Decision based on total score: APPROVE / CHALLENGE / REVIEW / DECLINE
- Git commit

---

## How the Rule Engine Works

```
FOR EVERY TRANSACTION:

1. Start with score = 0

2. Run EACH rule (order doesn't matter):
   ├── Rule: Velocity Check
   │   "Has this card been used >5 times in last 5 minutes?"
   │   If yes: score += 30
   │
   ├── Rule: Large Amount
   │   "Is amount > ₹50,000?"
   │   If yes: score += 20
   │   "Is amount > ₹2,00,000?"
   │   If yes: score += 40 (additional)
   │
   ├── Rule: Night Transaction
   │   "Is it between 1AM-5AM AND amount > ₹10,000?"
   │   If yes: score += 15
   │
   ├── Rule: New Device
   │   "Has this device fingerprint been seen before?"
   │   If new: score += 20
   │
   ├── Rule: Geography Anomaly
   │   "Is transaction from a different country than usual?"
   │   If yes: score += 35
   │
   ├── Rule: Multiple Declines
   │   "Were there >3 declined attempts in last hour?"
   │   If yes: score += 25
   │
   └── Rule: First Transaction
       "Is this the very first transaction on this card AND > ₹20,000?"
       If yes: score += 15

3. TOTAL SCORE → DECISION:
   ├── 0-40:   APPROVE (auto-approve, no extra verification)
   ├── 41-70:  CHALLENGE (require 3D Secure OTP for extra verification)
   ├── 71-90:  REVIEW (put in manual review queue for ops team)
   └── 91-100: DECLINE (auto-reject, do NOT call bank)
```

---

## Step 3.1: FraudRule Interface

```java
package com.payflow.payment.fraud;

/**
 * Each fraud detection rule implements this interface.
 * Rules are pluggable — add/remove rules without changing main code.
 */
public interface FraudRule {
    /**
     * Evaluate this rule against the transaction.
     * @return Score points to ADD (0 if rule doesn't trigger)
     */
    int evaluate(TransactionContext context);
    
    /** Rule name for logging */
    String getRuleName();
}
```

---

## Step 3.2: Example Rules

```java
// Large Amount Rule
public class LargeAmountRule implements FraudRule {
    @Override
    public int evaluate(TransactionContext ctx) {
        int score = 0;
        if (ctx.getAmount().compareTo(new BigDecimal("50000")) > 0) score += 20;
        if (ctx.getAmount().compareTo(new BigDecimal("200000")) > 0) score += 20;
        return score;
    }
    @Override public String getRuleName() { return "LARGE_AMOUNT"; }
}

// Velocity Rule
public class VelocityRule implements FraudRule {
    @Override
    public int evaluate(TransactionContext ctx) {
        // Check Redis: how many transactions from this card in last 5 min?
        int recentCount = getRecentTransactionCount(ctx.getCardHash(), Duration.ofMinutes(5));
        return recentCount > 5 ? 30 : 0;
    }
    @Override public String getRuleName() { return "VELOCITY"; }
}
```

---

## Step 3.3: FraudEngine (Runs All Rules)

```java
@Service
public class FraudEngine {
    private final List<FraudRule> rules; // Spring injects all FraudRule beans

    public FraudResult evaluate(TransactionContext context) {
        int totalScore = 0;
        List<String> triggeredRules = new ArrayList<>();

        for (FraudRule rule : rules) {
            int points = rule.evaluate(context);
            if (points > 0) {
                totalScore += points;
                triggeredRules.add(rule.getRuleName() + " (+" + points + ")");
            }
        }

        totalScore = Math.min(totalScore, 100); // Cap at 100
        String decision = totalScore <= 40 ? "APPROVE"
                        : totalScore <= 70 ? "CHALLENGE"
                        : totalScore <= 90 ? "REVIEW"
                        : "DECLINE";

        return new FraudResult(totalScore, decision, triggeredRules);
    }
}
```

---

## Interview Notes

**Q: "How does your fraud detection work?"**
> "Rule-based engine with pluggable rules. Each rule evaluates the transaction and contributes points to a risk score (0-100). Rules check velocity (too many transactions), amount thresholds, time of day, device fingerprint, and geographic anomalies. Total score determines the decision: auto-approve under 40, require OTP for 41-70, manual review for 71-90, auto-decline above 90. Rules are configurable in the database — we can add/modify without redeploying."

---

## Next Step → Phase 10 Parts 4-5
