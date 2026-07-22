package com.payflow.merchant;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.merchant", "com.payflow.common"})
@OpenAPIDefinition(info = @Info(
        title = "PayFlow Merchant Service API",
        version = "1.0",
        description = "Merchant onboarding, API key management, and fee configuration"
))
public class MerchantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
