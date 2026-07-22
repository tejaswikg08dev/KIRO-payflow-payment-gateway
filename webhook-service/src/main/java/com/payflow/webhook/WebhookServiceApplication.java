package com.payflow.webhook;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.payflow.webhook", "com.payflow.common"})
@OpenAPIDefinition(info = @Info(
        title = "PayFlow Webhook Service API",
        version = "1.0",
        description = "Reliable webhook delivery with HMAC signing and exponential backoff retry"
))
public class WebhookServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebhookServiceApplication.class, args);
    }
}
