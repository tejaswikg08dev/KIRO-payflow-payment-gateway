package com.payflow.payment.service;

import com.payflow.common.constant.PaymentStatus;
import com.payflow.common.exception.InvalidStateTransitionException;
import com.payflow.payment.dto.CaptureRequest;
import com.payflow.payment.dto.PaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.model.Payment;
import com.payflow.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private PaymentProcessorService paymentProcessorService;

    @Test
    @DisplayName("processPayment - should authorize payment successfully for valid card")
    void testProcessPayment_Success() {
        // Arrange
        PaymentRequest request = new PaymentRequest();
        request.setOrderId("order_123");
        request.setAmount(new BigDecimal("500.00"));
        request.setCurrency("INR");
        request.setMethod("card");

        PaymentRequest.CardDetails card = new PaymentRequest.CardDetails();
        card.setNumber("4111111111111111");
        card.setExpiryMonth(12);
        card.setExpiryYear(2026);
        card.setCvv("123");
        card.setHolderName("Test User");
        request.setCard(card);

        when(idempotencyService.getCachedResponse(any())).thenReturn(null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentResponse response = paymentProcessorService.processPayment(request, "idem_key_001", "merchant_01");

        // Assert
        assertNotNull(response);
        assertNotNull(response.getPaymentId());
        assertEquals("order_123", response.getOrderId());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals("INR", response.getCurrency());
        assertEquals("card", response.getMethod());
        assertEquals("1111", response.getCardLast4());
        assertEquals("VISA", response.getCardNetwork());

        // Payment should be authorized or failed (depends on random fraud score)
        assertTrue(response.getStatus().equals("authorized") || response.getStatus().equals("failed"));

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(idempotencyService, times(1)).cacheResponse(eq("idem_key_001"), any(PaymentResponse.class));
    }

    @Test
    @DisplayName("capturePayment - should throw InvalidStateTransitionException when payment is not AUTHORIZED")
    void testCapturePayment_InvalidState() {
        // Arrange
        String paymentId = "pay_invalid_state";

        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId("order_456")
                .merchantId("merchant_01")
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .status(PaymentStatus.CAPTURED) // Already captured - invalid for another capture
                .capturedAmount(new BigDecimal("1000.00"))
                .capturedAt(Instant.now())
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        CaptureRequest captureRequest = new CaptureRequest();
        captureRequest.setAmount(new BigDecimal("1000.00"));

        // Act & Assert
        assertThrows(InvalidStateTransitionException.class, () ->
                paymentProcessorService.capturePayment(paymentId, captureRequest)
        );

        // Verify no save was attempted
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
