package com.payflow.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Service Registry — The "phone book" of our microservices.
 * 
 * What this does:
 * - All services register themselves here on startup
 * - Services find each other by NAME (not IP address)
 * - If a service moves to a new IP/port, Eureka handles it automatically
 * - Dashboard available at: http://localhost:8761
 * 
 * How it works:
 * 1. This server starts on port 8761
 * 2. identity-service starts → registers as "IDENTITY-SERVICE" at 192.168.1.5:8081
 * 3. payment-service starts → registers as "PAYMENT-SERVICE" at 192.168.1.5:8083
 * 4. When payment-service needs identity-service:
 *    - Asks Eureka: "Where is IDENTITY-SERVICE?"
 *    - Eureka says: "It's at 192.168.1.5:8081"
 *    - payment-service calls that address
 * 
 * Why is this better than hardcoding URLs?
 * - Services can move between servers without config changes
 * - Multiple instances of same service → Eureka load-balances
 * - Dead services get removed automatically (heartbeat check)
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}
