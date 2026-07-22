# Hands-On Guide — Phase 6 Part 2: Payment State Machine

## Goal

By the end of Part 2, you will have:
- PaymentStateMachine class that validates transitions
- Understanding of which state changes are allowed
- State history tracking (audit trail)
- Git commit

## Prerequisites

- Part 1 completed (tables exist, service starts)

---

## What Is a State Machine?

A state machine is a set of rules defining:
1. **What states** an entity can be in (CREATED, AUTHORIZED, CAPTURED...)
2. **What events** can happen (authorize, capture, void, refund...)
3. **Which transitions** are valid (AUTHORIZED + capture → CAPTURED ✓)
4. **Which transitions** are INVALID (VOIDED + capture → ERROR ✗)

```
Think of it like a vending machine:
├── State: WAITING_FOR_COIN
│   ├── Event: insert_coin → State: WAITING_FOR_SELECTION ✓
│   └── Event: press_button → ERROR (no coin yet!) ✗
├── State: WAITING_FOR_SELECTION
│   ├── Event: press_button → State: DISPENSING ✓
│   └── Event: insert_coin → ERROR (already have coin!) ✗
└── State: DISPENSING
    └── Event: item_dispensed → State: WAITING_FOR_COIN ✓
```

---

## Our Payment States & Transitions

```
┌────────────────────────────────────────────────────────────────────────┐
│                     VALID TRANSITIONS MAP                                │
│                                                                          │
│  Current State    + Event           = Next State                        │
│  ─────────────────────────────────────────────────                      │
│  CREATED          + AUTHORIZE       = PROCESSING                        │
│  CREATED          + TIMEOUT         = EXPIRED                           │
│  PROCESSING       + BANK_APPROVED   = AUTHORIZED                        │
│  PROCESSING       + BANK_DECLINED   = FAILED                            │
│  AUTHORIZED       + CAPTURE         = CAPTURED                          │
│  AUTHORIZED       + VOID            = VOIDED                            │
│  AUTHORIZED       + TIMEOUT         = EXPIRED (7-day auth expiry)       │
│  CAPTURED         + REFUND          = REFUNDED (if full amount)         │
│  CAPTURED         + REFUND_PARTIAL  = CAPTURED (partial, still captured)│
│  CAPTURED         + SETTLE          = SETTLED                           │
│                                                                          │
│  INVALID TRANSITIONS (throw exception):                                 │
│  VOIDED + anything       → "Cannot perform action on voided payment"    │
│  FAILED + anything       → "Cannot perform action on failed payment"    │
│  SETTLED + CAPTURE       → "Already settled"                            │
│  CREATED + CAPTURE       → "Must authorize first"                       │
│  AUTHORIZED + REFUND     → "Must capture before refunding"              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Step 2.1: Create PaymentStateMachine

**Create file:** `payment-service/src/main/java/com/payflow/payment/statemachine/PaymentStateMachine.java`

```java
package com.payflow.payment.statemachine;

import com.payflow.common.constant.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Payment State Machine — enforces which state transitions are valid.
 * 
 * Why do we need this?
 * Without it, a bug or malicious API call could:
 * - Capture a payment that was already voided (double money movement!)
 * - Refund a payment that was never captured (free money!)
 * - Authorize an already-expired order
 * 
 * The state machine PREVENTS all invalid operations at the business logic level.
 */
@Component
public class PaymentStateMachine {

    /**
     * Map of: Current State → Set of valid next states.
     * If a transition is NOT in this map, it's ILLEGAL.
     */
    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
        PaymentStatus.CREATED, Set.of(
            PaymentStatus.PROCESSING,  // Customer submitted payment details
            PaymentStatus.EXPIRED      // 30-min timeout
        ),
        PaymentStatus.PROCESSING, Set.of(
            PaymentStatus.AUTHORIZED,  // Bank approved
            PaymentStatus.FAILED       // Bank declined
        ),
        PaymentStatus.AUTHORIZED, Set.of(
            PaymentStatus.CAPTURED,    // Merchant confirmed
            PaymentStatus.VOIDED,      // Merchant cancelled
            PaymentStatus.EXPIRED      // 7-day auth expiry
        ),
        PaymentStatus.CAPTURED, Set.of(
            PaymentStatus.REFUNDED,    // Full refund
            PaymentStatus.SETTLED      // Daily batch settlement
            // Note: Partial refund stays in CAPTURED state (refunded_amount increases)
        )
        // VOIDED, FAILED, EXPIRED, SETTLED, REFUNDED = terminal states (no transitions out)
    );

    /**
     * Validate and perform a state transition.
     * 
     * @param currentState The payment's current state
     * @param targetState The state we want to move to
     * @param action Human-readable action name (for error messages)
     * @throws InvalidStateTransitionException if transition is not valid
     */
    public void validateTransition(PaymentStatus currentState, PaymentStatus targetState, String action) {
        Set<PaymentStatus> allowedNextStates = VALID_TRANSITIONS.get(currentState);

        // Terminal states have no transitions out
        if (allowedNextStates == null) {
            throw new InvalidStateTransitionException(
                currentState.name(), action);
            // "Cannot capture. Current status: 'VOIDED'. This action is not allowed in this state."
        }

        // Check if target state is in the allowed set
        if (!allowedNextStates.contains(targetState)) {
            throw new InvalidStateTransitionException(
                currentState.name(), action);
            // "Cannot refund. Current status: 'AUTHORIZED'. This action is not allowed in this state."
            // (Must capture before refunding)
        }

        // Transition is valid! ✓
    }

    /**
     * Check if a transition is valid (without throwing exception).
     * Used for UI display ("grey out Capture button if not authorized").
     */
    public boolean canTransition(PaymentStatus currentState, PaymentStatus targetState) {
        Set<PaymentStatus> allowed = VALID_TRANSITIONS.get(currentState);
        return allowed != null && allowed.contains(targetState);
    }

    /**
     * Get all possible next states from current state.
     * Used for Swagger docs and frontend display.
     */
    public Set<PaymentStatus> getNextStates(PaymentStatus currentState) {
        return VALID_TRANSITIONS.getOrDefault(currentState, Set.of());
    }
}
```

---

## Step 2.2: How the State Machine Is Used

```java
// In PaymentProcessorService.capturePayment():

public PaymentResponse capturePayment(String paymentId, CaptureRequest request) {
    Payment payment = findPayment(paymentId);
    
    // STATE MACHINE CHECK:
    // Current state must be AUTHORIZED to allow capture
    stateMachine.validateTransition(
        payment.getStatus(),         // Current: AUTHORIZED
        PaymentStatus.CAPTURED,      // Target: CAPTURED
        "capture"                    // Action name for error message
    );
    // If current state is VOIDED → throws:
    // "Cannot capture. Current status: 'VOIDED'. This action is not allowed in this state."
    
    // If we reach here, transition is valid!
    payment.setStatus(PaymentStatus.CAPTURED);
    payment.setCapturedAmount(captureAmount);
    payment.setCapturedAt(Instant.now());
    paymentRepository.save(payment);
    
    return toResponse(payment);
}
```

---

## Step 2.3: Test State Machine Logic

| Starting State | Action | Expected Result |
|---------------|--------|-----------------|
| CREATED | authorize | → PROCESSING ✓ |
| PROCESSING | bank approves | → AUTHORIZED ✓ |
| AUTHORIZED | capture | → CAPTURED ✓ |
| AUTHORIZED | void | → VOIDED ✓ |
| CAPTURED | refund | → REFUNDED ✓ |
| CAPTURED | settle | → SETTLED ✓ |
| **VOIDED** | **capture** | **InvalidStateTransitionException** ✗ |
| **FAILED** | **capture** | **InvalidStateTransitionException** ✗ |
| **CREATED** | **capture** | **InvalidStateTransitionException** ✗ |
| **AUTHORIZED** | **refund** | **InvalidStateTransitionException** ✗ |
| **SETTLED** | **void** | **InvalidStateTransitionException** ✗ |

---

## Step 2.4: Git Commit

```cmd
git add payment-service/src/main/java/com/payflow/payment/statemachine/
git commit -m "Phase 6 Part 2: Payment state machine - validates allowed transitions"
```

---

## What We Built

| File | Purpose |
|------|---------|
| `statemachine/PaymentStateMachine.java` | Validates state transitions, prevents invalid operations |

---

## Interview Notes

**Q: "How do you prevent invalid payment operations?"**
> "I implemented a state machine with a transitions map. Before any state change (capture, void, refund), the system checks if the transition is valid from the current state. For example, you can't capture a voided payment or refund an authorized payment. Invalid transitions throw a 400 error with a clear message."

**Q: "Why not use Spring State Machine library?"**
> "Spring State Machine is powerful but complex for our use case. Our payments have a simple, well-defined state graph. A plain Map<State, Set<NextStates>> is simpler, faster, and easier to test. We'd use Spring State Machine for more complex workflows with guards, actions, and hierarchical states."

---

## Next Step

→ Continue to **Phase 6 Part 3: Order Creation**
