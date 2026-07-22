package com.payflow.payment.statemachine;

import com.payflow.common.constant.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> VALID_TRANSITIONS = Map.of(
        PaymentStatus.CREATED, Set.of(PaymentStatus.PROCESSING, PaymentStatus.EXPIRED),
        PaymentStatus.PROCESSING, Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED),
        PaymentStatus.AUTHORIZED, Set.of(PaymentStatus.CAPTURED, PaymentStatus.VOIDED, PaymentStatus.EXPIRED),
        PaymentStatus.CAPTURED, Set.of(PaymentStatus.REFUNDED, PaymentStatus.SETTLED)
    );

    public void validateTransition(PaymentStatus currentState, PaymentStatus targetState, String action) {
        Set<PaymentStatus> allowedNextStates = VALID_TRANSITIONS.get(currentState);
        if (allowedNextStates == null || !allowedNextStates.contains(targetState)) {
            throw new InvalidStateTransitionException(currentState.name(), action);
        }
    }

    public boolean canTransition(PaymentStatus currentState, PaymentStatus targetState) {
        Set<PaymentStatus> allowed = VALID_TRANSITIONS.get(currentState);
        return allowed != null && allowed.contains(targetState);
    }
}
