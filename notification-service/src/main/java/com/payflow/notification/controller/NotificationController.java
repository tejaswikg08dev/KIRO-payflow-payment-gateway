package com.payflow.notification.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.notification.dto.NotificationRequest;
import com.payflow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Internal notification dispatch endpoint")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Send a notification (email or SMS) to a recipient.
     * This is an internal API used by other services (payment, webhook, etc.).
     */
    @PostMapping("/notify")
    @Operation(summary = "Send notification", description = "Dispatches an email or SMS notification based on the request type")
    public ResponseEntity<ApiResponse<String>> sendNotification(@RequestBody NotificationRequest request) {
        log.info("Received notification request: type={}, recipient={}, template={}",
                request.type(), request.recipient(), request.template());

        switch (request.type().toLowerCase()) {
            case "email" -> notificationService.sendEmail(request);
            case "sms" -> notificationService.sendSms(request);
            default -> {
                log.warn("Unsupported notification type: {}", request.type());
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("INVALID_TYPE", "Unsupported notification type: " + request.type()));
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Notification sent successfully"));
    }
}
