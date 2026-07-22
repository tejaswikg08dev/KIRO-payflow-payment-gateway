package com.payflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — The single entry point for ALL external requests.
 * 
 * What this does:
 * 1. All external traffic comes to port 8080
 * 2. Gateway examines the URL path
 * 3. Routes the request to the correct internal service:
 *    /v1/auth/**       → identity-service (8081)
 *    /v1/merchants/**  → merchant-service (8082)
 *    /v1/orders/**     → payment-service (8083)
 *    /v1/payments/**   → payment-service (8083)
 *    /v1/settlements/**→ settlement-service (8085)
 *    /v1/webhooks/**   → webhook-service (8086)
 * 4. Also provides:
 *    - Rate limiting (100 req/sec per API key)
 *    - Authentication validation (check JWT/API key)
 *    - Request logging (correlation IDs)
 *    - Aggregated Swagger UI (all services in one page)
 * 
 * Client sees: http://api.payflow.com/v1/payments
 * Internally routes to: http://PAYMENT-SERVICE/v1/payments
 * Client never knows about internal services!
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
