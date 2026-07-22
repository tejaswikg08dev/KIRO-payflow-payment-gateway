package com.payflow.settlement.repository;

import com.payflow.settlement.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, String> {
    List<Settlement> findByMerchantIdOrderBySettlementDateDesc(String merchantId);
    Optional<Settlement> findByMerchantIdAndSettlementDate(String merchantId, LocalDate date);
    List<Settlement> findByStatus(Settlement.SettlementStatus status);
}
