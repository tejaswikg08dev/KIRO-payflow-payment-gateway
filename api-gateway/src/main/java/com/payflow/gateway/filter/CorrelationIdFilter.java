package com.payflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Correlation ID Filter — Adds a unique trace ID to every request.
 * 
 * Purpose:
 * - Every request gets a unique X-Correlation-Id header
 * - This ID is passed to all downstream services
 * - All services include it in their logs
 * - When debugging: search logs by correlation ID → see entire request journey
 * 
 * Example:
 * Customer calls POST /v1/payments
 * → Gateway adds: X-Correlation-Id: req_a1b2c3d4-e5f6-7890
 * → Payment Service logs: [req_a1b2c3d4] Processing payment...
 * → Routing Service logs: [req_a1b2c3d4] Routing to HDFC...
 * → Bank Simulator logs: [req_a1b2c3d4] Received auth request...
 * 
 * Now you can trace the entire flow in CloudWatch with ONE search!
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Check if request already has a correlation ID (from upstream, e.g., ALB)
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            // Generate new one
            correlationId = "req_" + UUID.randomUUID().toString().substring(0, 12);
        }

        // Add to request headers (passed to downstream services)
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_HEADER, correlationId)
                .build();

        // Add to response headers (client can see it for debugging)
        String finalCorrelationId = correlationId;
        exchange.getResponse().getHeaders().add(CORRELATION_HEADER, finalCorrelationId);

        log.info("[{}] {} {}", finalCorrelationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -2; // Run FIRST (before rate limiting, before routing)
    }
}
