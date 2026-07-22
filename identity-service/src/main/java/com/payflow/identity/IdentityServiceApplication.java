package com.payflow.identity;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.identity", "com.payflow.common"})
@OpenAPIDefinition(
        info = @Info(
                title = "PayFlow Identity Service API",
                version = "1.0",
                description = "User registration, login, and JWT token management",
                contact = @Contact(name = "PayFlow Team")
        )
)
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
