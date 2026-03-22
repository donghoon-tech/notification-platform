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


@Slf4j
@Service
@RequiredArgsConstructor
public class DispatcherService {

    private final DeliveryLogRepository deliveryLogRepository;
    private final NotificationRequestRepository notificationRequestRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.notification.platform.config.SnowflakeIdGenerator snowflakeIdGenerator;
    private final PresenceManager presenceManager;

    @Transactional
    public void dispatch(NotificationRequestEvent event) {
        log.info("Dispatching notification request: {} for channel: {}", event.getRequestId(), event.getChannel());

        // 1. Fetch the original request
        NotificationRequest request = notificationRequestRepository.findById(event.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + event.getRequestId()));

        // 2. Presence Check & Fallback Logic (Only for IN_APP)
        if (event.getChannel() == NotificationChannel.IN_APP && !presenceManager.isOnline(event.getRecipientId())) {
            log.info("User {} is offline. Rerouting IN_APP request {} to EMAIL.", event.getRecipientId(), event.getRequestId());
            
            // Create a REROUTED log for the original IN_APP attempt
            DeliveryLog reroutedLog = DeliveryLog.builder()
                    .id(snowflakeIdGenerator.nextId())
                    .request(request)
                    .recipientId(event.getRecipientId())
                    .channel(NotificationChannel.IN_APP)
                    .targetAddress(event.getTargetAddress())
                    .status(DeliveryStatus.REROUTED)
                    .errorMessage("User offline, falling back to EMAIL")
                    .build();
            deliveryLogRepository.save(reroutedLog);

            // Reroute by calling dispatch again with EMAIL channel
            NotificationRequestEvent fallbackEvent = event.toBuilder()
                    .channel(NotificationChannel.EMAIL)
                    .build();
            
            dispatch(fallbackEvent);
            return;
        }

        // 3. Create DeliveryLog (Track attempt)
        DeliveryLog deliveryLog = DeliveryLog.builder()
                .id(snowflakeIdGenerator.nextId())
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
                
                // Update status to QUEUED after successful Kafka send (relies on JPA dirty checking)
                deliveryLog.updateStatus(DeliveryStatus.QUEUED);
            } catch (Exception e) {
                log.error("Failed to route to Kafka: {}", e.getMessage());
                deliveryLog.updateStatus(DeliveryStatus.FAILED, e.getMessage());
            }
        } else {
            log.warn("Unknown channel: {} for request: {}", event.getChannel(), event.getRequestId());
            deliveryLog.updateStatus(DeliveryStatus.FAILED, "Unsupported channel: " + event.getChannel());
        }
    }

    private String determineTopic(NotificationChannel channel) {
        return switch (channel) {
            case IN_APP -> "notification.inapp";
            case EMAIL -> "notification.email";
        };
    }
}
