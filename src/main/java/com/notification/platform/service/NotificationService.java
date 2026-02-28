package com.notification.platform.service;

import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRequestRepository repository;

    @Transactional
    public NotificationSendResponse triggerNotification(NotificationSendRequest request) {
        // Idempotency check (simplified for now, using DB constraint exception later or explicit find)
        // In a real high-traffic scenario, this should be checked in Redis first.
        
        // Build the entity from request
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .id(UUID.randomUUID()) // Replace with Snowflake ID generator in the future
                .idempotencyKey(request.getIdempotencyKey())
                .producerName(request.getProducerName())
                .priority(request.getPriority())
                .payload(request.getPayload())
                .build();

        try {
            repository.save(notificationRequest);
            log.info("Notification request saved: {}", notificationRequest.getId());
            
            // TODO: Next step - Send to internal message bus (Kafka)
            // for now, we just return the success
            
            return NotificationSendResponse.builder()
                    .requestId(notificationRequest.getId())
                    .status("ACCEPTED")
                    .build();
        } catch (Exception e) {
            // Simplified idempotency handling: if key exists, we might want to return existing ID
            // For now, let's just log and throw if it's not a duplicate key error
            log.warn("Failed to save notification request (possible duplicate key): {}", e.getMessage());
            throw e; 
        }
    }
}
