package com.payflow.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server — Centralized configuration management.
 * 
 * What this does:
 * - Stores configuration (YAML) for ALL services in one place
 * - Each service fetches its config from here on startup
 * - Change config in one place → all services get updated
 * 
 * How services get their config:
 * 1. identity-service starts
 * 2. It calls: http://config-server:8888/identity-service/default
 * 3. Config Server returns identity-service.yml content
 * 4. identity-service uses those settings (DB URL, port, secrets, etc.)
 * 
 * Config files location: config-server/configurations/ folder
 * Each service has its own YAML: identity-service.yml, payment-service.yml, etc.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
