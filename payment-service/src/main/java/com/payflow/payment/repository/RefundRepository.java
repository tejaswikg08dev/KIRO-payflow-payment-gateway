package com.payflow.payment.repository;

import com.payflow.payment.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, String> {
    List<Refund> findByPaymentId(String paymentId);
    List<Refund> findByMerchantId(String merchantId);
}
