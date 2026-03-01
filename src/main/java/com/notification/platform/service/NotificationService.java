package com.notification.platform.service;

import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRequestRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "notification.requests";

    @Transactional
    public NotificationSendResponse triggerNotification(NotificationSendRequest request) {
        // Build the entity from request
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(request.getIdempotencyKey())
                .producerName(request.getProducerName())
                .priority(request.getPriority())
                .payload(request.getPayload())
                .build();

        try {
            repository.save(notificationRequest);
            log.info("Notification request saved to DB: {}", notificationRequest.getId());

            // Publish event to Kafka
            NotificationRequestEvent event = NotificationRequestEvent.builder()
                    .requestId(notificationRequest.getId())
                    .recipientId(request.getRecipientId())
                    .channel(request.getChannel())
                    .targetAddress(request.getTargetAddress())
                    .priority(request.getPriority())
                    .payload(request.getPayload())
                    .build();

            kafkaTemplate.send(TOPIC, request.getRecipientId(), event);
            log.info("Notification request published to Kafka: {}", notificationRequest.getId());

            return NotificationSendResponse.builder()
                    .requestId(notificationRequest.getId())
                    .status(NotificationIngressStatus.ACCEPTED)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to process notification request: {}", e.getMessage());
            throw e;
        }
    }
}
