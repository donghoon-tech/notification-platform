package com.notification.platform.messaging.adapter;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppAdapter {

    private final SimpMessagingTemplate messagingTemplate;
    private final DeliveryLogRepository deliveryLogRepository;

    @Transactional
    @KafkaListener(topics = "${spring.kafka.topic.inapp}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(NotificationRequestEvent event) {
        log.info("InAppAdapter consumed event for request: {}", event.getRequestId());

        try {
            // 1. Deliver the message via WebSocket STOMP
            String destination = "/topic/notifications/" + event.getRecipientId();
            messagingTemplate.convertAndSend(destination, event.getPayload());
            log.info("Message pushed to WebSocket destination: {}", destination);

            // 2. Tracking: Update DeliveryLog status
            updateDeliveryStatus(event.getRequestId(), DeliveryStatus.DELIVERED);

        } catch (Exception e) {
            log.error("Failed to push In-App notification: {}", e.getMessage(), e);
            updateDeliveryStatus(event.getRequestId(), DeliveryStatus.FAILED);
        }
    }

    private void updateDeliveryStatus(java.util.UUID requestId, DeliveryStatus status) {
        deliveryLogRepository.findByRequestIdAndChannel(requestId, NotificationChannel.IN_APP)
                .ifPresent(deliveryLog -> {
                    DeliveryLog updatedLog = DeliveryLog.builder()
                            .id(deliveryLog.getId())
                            .request(deliveryLog.getRequest())
                            .recipientId(deliveryLog.getRecipientId())
                            .channel(deliveryLog.getChannel())
                            .targetAddress(deliveryLog.getTargetAddress())
                            .status(status)
                            .createdAt(deliveryLog.getCreatedAt())
                            .build();
                    deliveryLogRepository.save(updatedLog);
                    log.info("DeliveryLog updated to {} for request: {}", status, requestId);
                });
    }
}
