package com.notification.platform.messaging.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "notification.requests";

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationRequestCreated(NotificationRequestCreatedEvent event) {
        log.info("Transaction committed. Publishing notification request to Kafka: {}", event.getRequestId());

        NotificationRequestEvent kafkaEvent = NotificationRequestEvent.builder()
                .requestId(event.getRequestId())
                .recipientId(event.getRecipientId())
                .channel(event.getChannel())
                .targetAddress(event.getTargetAddress())
                .priority(event.getPriority())
                .payload(event.getPayload())
                .build();

        try {
            kafkaTemplate.send(TOPIC, event.getRecipientId(), kafkaEvent);
            log.info("Successfully published to Kafka for request: {}", event.getRequestId());
        } catch (Exception e) {
            log.error("Failed to publish to Kafka after commit for request: {}. Error: {}", event.getRequestId(), e.getMessage());
        }
    }
}
