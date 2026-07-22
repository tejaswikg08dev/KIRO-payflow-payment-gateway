# Hands-On Guide — Phase 10 Part 4: AI Risk Scoring (ML Model)

## Goal
- ML-based fraud scoring using Decision Tree
- Combined score: (Rules × 0.6) + (ML × 0.4) = final score
- Training on simulated transaction data
- Fast inference (<10ms per transaction)

---

## AI Layer on Top of Rules

```
RULE ENGINE alone: Good, but limited to explicit rules we define.
ML MODEL addition: Detects PATTERNS we haven't explicitly coded.

EXAMPLE:
├── Rules catch: "amount > ₹50,000" → +20 points
├── ML catches: "This combination of {merchant_category=electronics} + 
│   {time=3AM} + {amount=₹45,000} + {new_device=true} 
│   historically leads to 80% fraud" → ML score: 75
└── Combined: More accurate than either alone

FORMULA:
final_score = (rule_score × 0.6) + (ml_score × 0.4)
├── Rules have 60% weight (reliable, explainable)
└── ML has 40% weight (catches patterns, but less explainable)
```

---

## Decision Tree Model

```
WHY DECISION TREE (not Neural Network)?
├── Fast inference: <1ms (runs in same JVM, no network call)
├── Explainable: "Declined because amount > 50K AND new device AND night time"
├── Small model: Kilobytes (not gigabytes)
├── No GPU needed: Runs on any server
├── Good enough: 85-90% fraud detection accuracy for payment patterns
└── No external dependency: No Python, no TensorFlow, no API calls

FEATURES (input to model):
├── amount (normalized 0-1)
├── hour_of_day (0-23)
├── velocity_5min (transactions in last 5 minutes)
├── device_age_days (how long has this device been seen)
├── merchant_risk_category (0-3: low, medium, high, very high)
├── card_country_match (0 or 1: does card country match transaction country?)
└── previous_decline_count (declines in last 24 hours)

OUTPUT: fraud_probability (0.0 to 1.0) → scaled to 0-100 score
```

---

## Implementation in Java (Using Weka/Smile)

```java
// Simplified Decision Tree (custom implementation):
public class FraudMLScorer {
    
    public int score(TransactionFeatures features) {
        // Decision tree logic (generated from training):
        if (features.getAmount() > 50000) {
            if (features.getDeviceAgeDays() < 1) {
                if (features.getHourOfDay() >= 1 && features.getHourOfDay() <= 5) {
                    return 85; // High risk: large + new device + night
                }
                return 65; // Medium: large + new device
            }
            return 35; // Lower: large but known device
        }
        if (features.getVelocity5min() > 5) {
            return 70; // High velocity
        }
        if (features.getPreviousDeclines() > 3) {
            return 55; // Multiple recent failures
        }
        return 15; // Normal transaction
    }
}
```

---

## Interview Notes

**Q: "What AI/ML do you use for fraud detection?"**
> "A two-layer approach: rule engine (60% weight) for explicit patterns like velocity and amount thresholds, plus a decision tree ML model (40% weight) trained on historical transaction data. The ML model catches patterns across multiple features that individual rules might miss. Inference is <1ms since the model runs in the same JVM — no external API call needed."

**Q: "How do you train the model?"**
> "For this project, I use simulated training data with labeled fraud/legitimate transactions. Features include amount, time of day, velocity, device age, and merchant category. In production, we'd retrain weekly on real data with confirmed fraud cases."

---

## Next Step → Phase 10 Part 5
