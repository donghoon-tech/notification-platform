package com.notification.platform.dispatcher;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
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
public class DispatcherService {

    private final DeliveryLogRepository deliveryLogRepository;
    private final NotificationRequestRepository notificationRequestRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void dispatch(NotificationRequestEvent event) {
        log.info("Dispatching notification request: {}", event.getRequestId());

        // 1. Fetch the original request
        NotificationRequest request = notificationRequestRepository.findById(event.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + event.getRequestId()));

        // 2. Create DeliveryLog (Track attempt)
        DeliveryLog deliveryLog = DeliveryLog.builder()
                .id(UUID.randomUUID())
                .request(request)
                .recipientId(event.getRecipientId())
                .channel(event.getChannel())
                .targetAddress(event.getTargetAddress())
                .status(DeliveryStatus.PENDING)
                .build();

        deliveryLogRepository.save(deliveryLog);

        // 3. Route to channel-specific Kafka topics
        String targetTopic = determineTopic(event.getChannel());
        if (targetTopic != null) {
            try {
                kafkaTemplate.send(targetTopic, event.getRecipientId(), event);
                log.info("Routed to channel topic: {} for request: {}", targetTopic, event.getRequestId());
                
                // Update status to QUEUED after successful Kafka send
                deliveryLogRepository.save(updateStatus(deliveryLog, DeliveryStatus.QUEUED));
            } catch (Exception e) {
                log.error("Failed to route to Kafka: {}", e.getMessage());
                deliveryLogRepository.save(updateStatus(deliveryLog, DeliveryStatus.FAILED, e.getMessage()));
            }
        } else {
            log.warn("Unknown channel: {} for request: {}", event.getChannel(), event.getRequestId());
            deliveryLogRepository.save(updateStatus(deliveryLog, DeliveryStatus.FAILED, "Unsupported channel: " + event.getChannel()));
        }
    }

    private DeliveryLog updateStatus(DeliveryLog log, DeliveryStatus status) {
        return updateStatus(log, status, null);
    }

    private DeliveryLog updateStatus(DeliveryLog log, DeliveryStatus status, String errorMessage) {
        return DeliveryLog.builder()
                .id(log.getId())
                .request(log.getRequest())
                .recipientId(log.getRecipientId())
                .channel(log.getChannel())
                .targetAddress(log.getTargetAddress())
                .status(status)
                .errorMessage(errorMessage)
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String determineTopic(NotificationChannel channel) {
        return switch (channel) {
            case IN_APP -> "notification.inapp";
            case EMAIL -> "notification.email";
        };
    }
}
