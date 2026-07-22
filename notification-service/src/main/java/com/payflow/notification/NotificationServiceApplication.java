package com.payflow.notification;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.payflow.notification", "com.payflow.common"})
@OpenAPIDefinition(info = @Info(
        title = "PayFlow Notification Service API",
        version = "1.0",
        description = "Email/SMS notifications via AWS SNS"
))
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
