package com.payflow.routing;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.routing", "com.payflow.common"})
@OpenAPIDefinition(info = @Info(
        title = "PayFlow Routing Service API",
        version = "1.0",
        description = "Smart payment routing and ISO 8583 bank communication"
))
public class RoutingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoutingServiceApplication.class, args);
    }
}
