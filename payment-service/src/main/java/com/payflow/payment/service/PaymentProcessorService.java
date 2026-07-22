package com.payflow.payment.service;

import com.payflow.common.constant.PaymentMethod;
import com.payflow.common.constant.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;
import com.payflow.common.exception.PayflowException;
import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.payment.dto.CaptureRequest;
import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.model.Payment;
import com.payflow.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessorService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;

    /**
     * Process a new payment (authorize with bank).
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey, String merchantId) {
        // 1. Check idempotency
        if (idempotencyKey != null) {
            PaymentResponse cached = idempotencyService.getCachedResponse(idempotencyKey);
            if (cached != null) {
                log.info("Returning cached response for idempotency key: {}", idempotencyKey);
                return cached;
            }
        }

        // 2. Determine payment method
        PaymentMethod method = PaymentMethod.valueOf(request.getMethod().toUpperCase());

        // 3. Create payment record
        Payment payment = Payment.builder()
                .id(IdGenerator.paymentId())
                .orderId(request.getOrderId())
                .merchantId(merchantId != null ? merchantId : "default_merchant")
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .status(PaymentStatus.PROCESSING)
                .paymentMethod(method)
                .idempotencyKey(idempotencyKey)
                .build();

        // Set method-specific fields
        if (method == PaymentMethod.CARD && request.getCard() != null) {
            String cardNum = request.getCard().getNumber();
            payment.setCardLast4(cardNum.substring(cardNum.length() - 4));
            payment.setCardNetwork(detectCardNetwork(cardNum));
        } else if (method == PaymentMethod.UPI && request.getUpi() != null) {
            payment.setUpiVpa(request.getUpi().getVpa());
        }

        // 4. Simulate fraud check (score 0-100)
        int riskScore = simulateFraudCheck(payment);
        payment.setRiskScore(riskScore);

        // 5. Simulate bank authorization
        if (riskScore > 90) {
            // Auto-decline by fraud
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode("FRAUD_DETECTED");
            payment.setFailureReason("Transaction flagged as high-risk (score: " + riskScore + ")");
        } else {
            // Simulate bank approval (in real system, this calls routing-service → bank)
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setAuthCode(generateAuthCode());
            payment.setRrn(generateRrn());
            payment.setRouteId("HDFC_ACQ_01");
            payment.setAuthorizedAt(Instant.now());
        }

        // 6. Save to database
        paymentRepository.save(payment);
        log.info("Payment {} status: {}", payment.getId(), payment.getStatus());

        // 7. Build response
        PaymentResponse response = toResponse(payment);

        // 8. Cache for idempotency
        if (idempotencyKey != null) {
            idempotencyService.cacheResponse(idempotencyKey, response);
        }

        return response;
    }

    /**
     * Capture an authorized payment (money actually moves).
     */
    @Transactional
    public PaymentResponse capturePayment(String paymentId, CaptureRequest request) {
        Payment payment = findPayment(paymentId);

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidStateTransitionException(payment.getStatus().name(), "capture");
        }

        BigDecimal captureAmount = (request != null && request.getAmount() != null)
                ? request.getAmount()
                : payment.getAmount();

        if (captureAmount.compareTo(payment.getAmount()) > 0) {
            throw new PayflowException("AMOUNT_EXCEEDS_AUTHORIZED",
                    "Capture amount cannot exceed authorized amount", HttpStatus.BAD_REQUEST);
        }

        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAmount(captureAmount);
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);

        log.info("Payment {} captured: {}", paymentId, captureAmount);
        return toResponse(payment);
    }

    /**
     * Void an authorized payment (release the hold).
     */
    @Transactional
    public PaymentResponse voidPayment(String paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidStateTransitionException(payment.getStatus().name(), "void");
        }

        payment.setStatus(PaymentStatus.VOIDED);
        paymentRepository.save(payment);

        log.info("Payment {} voided", paymentId);
        return toResponse(payment);
    }

    /**
     * Get payment details.
     */
    public PaymentResponse getPayment(String paymentId) {
        return toResponse(findPayment(paymentId));
    }

    private Payment findPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .orderId(p.getOrderId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus().name().toLowerCase())
                .method(p.getPaymentMethod().name().toLowerCase())
                .cardLast4(p.getCardLast4())
                .cardNetwork(p.getCardNetwork())
                .upiVpa(p.getUpiVpa())
                .authCode(p.getAuthCode())
                .rrn(p.getRrn())
                .riskScore(p.getRiskScore())
                .routeUsed(p.getRouteId())
                .capturedAmount(p.getCapturedAmount())
                .refundedAmount(p.getRefundedAmount())
                .failureCode(p.getFailureCode())
                .failureReason(p.getFailureReason())
                .authorizedAt(p.getAuthorizedAt())
                .capturedAt(p.getCapturedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private String detectCardNetwork(String cardNumber) {
        if (cardNumber.startsWith("4")) return "VISA";
        if (cardNumber.startsWith("5")) return "MASTERCARD";
        if (cardNumber.startsWith("6")) return "RUPAY";
        return "UNKNOWN";
    }

    private int simulateFraudCheck(Payment payment) {
        // Simple simulation: random score 0-50 for most, higher for large amounts
        int score = (int) (Math.random() * 40);
        if (payment.getAmount().compareTo(new BigDecimal("50000")) > 0) score += 20;
        return Math.min(score, 100);
    }

    private String generateAuthCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(chars.charAt((int)(Math.random() * chars.length())));
        return sb.toString();
    }

    private String generateRrn() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append((int)(Math.random() * 10));
        return sb.toString();
    }
}
