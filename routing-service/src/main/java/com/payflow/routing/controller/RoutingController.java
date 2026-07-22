package com.payflow.routing.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.routing.dto.RouteRequest;
import com.payflow.routing.dto.RouteResponse;
import com.payflow.routing.service.RoutingEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/route")
@RequiredArgsConstructor
@Tag(name = "Routing", description = "Internal API — routes payments to banks via ISO 8583")
public class RoutingController {

    private final RoutingEngine routingEngine;

    @PostMapping
    @Operation(summary = "Route a payment to the best bank (internal use only)")
    public ResponseEntity<ApiResponse<RouteResponse>> routePayment(@RequestBody RouteRequest request) {
        RouteResponse response = routingEngine.routePayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
