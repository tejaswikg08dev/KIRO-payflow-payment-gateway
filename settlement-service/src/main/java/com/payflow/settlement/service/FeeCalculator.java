package com.payflow.settlement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates MDR fee and GST for settlement.
 * 
 * Formula:
 *   MDR Fee = gross_amount × mdr_percentage / 100
 *   GST on Fee = MDR Fee × 18 / 100
 *   Net Payout = gross_amount - refunds - MDR Fee - GST
 */
@Slf4j
@Service
public class FeeCalculator {

    private static final BigDecimal GST_RATE = new BigDecimal("18.00"); // 18% GST on MDR

    public FeeResult calculate(BigDecimal grossAmount, BigDecimal refundAmount, BigDecimal mdrPercentage) {
        BigDecimal netGross = grossAmount.subtract(refundAmount);

        BigDecimal mdrFee = netGross.multiply(mdrPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal gstOnFee = mdrFee.multiply(GST_RATE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal netPayout = netGross.subtract(mdrFee).subtract(gstOnFee);

        log.debug("Fee calc: gross={}, refunds={}, MDR({}%)={}, GST={}, net={}",
                grossAmount, refundAmount, mdrPercentage, mdrFee, gstOnFee, netPayout);

        return new FeeResult(mdrFee, gstOnFee, netPayout);
    }

    public record FeeResult(BigDecimal mdrFee, BigDecimal gstOnFee, BigDecimal netPayout) {}
}
