package com.notification.platform.api.controller;

import com.notification.platform.api.dto.request.AdminRetryRequest;
import com.notification.platform.service.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @PostMapping("/{requestId}/retry")
    public ResponseEntity<Void> retry(
            @PathVariable Long requestId,
            @RequestBody(required = false) AdminRetryRequest request) {
        
        adminNotificationService.triggerRetry(requestId, request);
        return ResponseEntity.accepted().build();
    }
}
