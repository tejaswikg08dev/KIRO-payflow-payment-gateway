package com.payflow.payment.service;

import com.payflow.common.exception.ResourceNotFoundException;
import com.payflow.common.util.IdGenerator;
import com.payflow.payment.dto.OrderRequest;
import com.payflow.payment.dto.OrderResponse;
import com.payflow.payment.model.Order;
import com.payflow.payment.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Value("${payment.order-expiry-minutes:30}")
    private int orderExpiryMinutes;

    @Transactional
    public OrderResponse createOrder(OrderRequest request, String merchantId) {
        Order order = Order.builder()
                .id(IdGenerator.orderId())
                .merchantId(merchantId)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .receipt(request.getReceipt())
                .notes(request.getNotes())
                .status(Order.OrderStatus.CREATED)
                .expiresAt(Instant.now().plus(orderExpiryMinutes, ChronoUnit.MINUTES))
                .build();

        orderRepository.save(order);
        log.info("Order created: {} (₹{}) for merchant {}", order.getId(), order.getAmount(), merchantId);
        return toResponse(order);
    }

    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return toResponse(order);
    }

    @Transactional
    public void markAsPaid(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        order.setStatus(Order.OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .receipt(order.getReceipt())
                .status(order.getStatus().name().toLowerCase())
                .notes(order.getNotes())
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
