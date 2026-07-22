package com.payflow.settlement.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.settlement.model.Settlement;
import com.payflow.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "View settlement records and payouts")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    @Operation(summary = "List settlements for a merchant")
    public ResponseEntity<ApiResponse<List<Settlement>>> listSettlements(
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId) {
        String resolved = merchantId != null ? merchantId : "merch_default";
        List<Settlement> settlements = settlementService.getSettlementsForMerchant(resolved);
        return ResponseEntity.ok(ApiResponse.success(settlements));
    }
}
