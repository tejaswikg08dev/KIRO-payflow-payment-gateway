package com.payflow.payment.repository;

import com.payflow.common.constant.PaymentStatus;
import com.payflow.payment.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Page<Payment> findByMerchantId(String merchantId, Pageable pageable);

    Page<Payment> findByMerchantIdAndStatus(String merchantId, PaymentStatus status, Pageable pageable);

    Optional<Payment> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    List<Payment> findByStatusAndCapturedAtBefore(PaymentStatus status, Instant before);
}
