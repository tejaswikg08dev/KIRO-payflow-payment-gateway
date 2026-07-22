package com.payflow.settlement.service;

import com.payflow.common.util.IdGenerator;
import com.payflow.settlement.model.Settlement;
import com.payflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final FeeCalculator feeCalculator;

    /**
     * Create a settlement record for a merchant for a given date.
     * Called by the scheduled batch job.
     */
    @Transactional
    public Settlement createSettlement(String merchantId, LocalDate date,
                                       BigDecimal grossAmount, BigDecimal refundAmount,
                                       BigDecimal mdrPercentage,
                                       int txnCount, int refundCount) {

        FeeCalculator.FeeResult fees = feeCalculator.calculate(grossAmount, refundAmount, mdrPercentage);

        Settlement settlement = Settlement.builder()
                .id(IdGenerator.settlementId())
                .merchantId(merchantId)
                .settlementDate(date)
                .grossAmount(grossAmount)
                .refundAmount(refundAmount)
                .feeAmount(fees.mdrFee())
                .gstOnFee(fees.gstOnFee())
                .netAmount(fees.netPayout())
                .totalTransactions(txnCount)
                .totalRefunds(refundCount)
                .status(Settlement.SettlementStatus.PROCESSED)
                .processedAt(Instant.now())
                .build();

        settlementRepository.save(settlement);
        log.info("Settlement created: {} for merchant {} (net: ₹{})", 
                settlement.getId(), merchantId, fees.netPayout());

        return settlement;
    }

    public List<Settlement> getSettlementsForMerchant(String merchantId) {
        return settlementRepository.findByMerchantIdOrderBySettlementDateDesc(merchantId);
    }
}
