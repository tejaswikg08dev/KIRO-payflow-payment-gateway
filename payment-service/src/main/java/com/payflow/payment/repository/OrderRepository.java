package com.payflow.payment.repository;

import com.payflow.payment.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByMerchantIdAndStatus(String merchantId, Order.OrderStatus status);
    List<Order> findByStatusAndExpiresAtBefore(Order.OrderStatus status, Instant before);
}
