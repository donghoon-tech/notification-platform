package com.notification.platform.messaging.event;

import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationRequestRepository repository;
    private static final String TOPIC = "notification.requests";

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationRequestCreated(NotificationRequestCreatedEvent event) {
        log.info("Starting post-commit Kafka dispatch for request: {}", event.getRequestId());

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
            updateStatus(event.getRequestId(), NotificationIngressStatus.DISPATCHED);
            log.info("Dispatched to Kafka and updated status to DISPATCHED: {}", event.getRequestId());
        } catch (Exception e) {
            log.error("Kafka dispatch failed for request: {}", event.getRequestId(), e);
            updateStatus(event.getRequestId(), NotificationIngressStatus.FAILED);
        }
    }

    private void updateStatus(Long requestId, NotificationIngressStatus status) {
        repository.findById(requestId).ifPresent(request -> request.updateStatus(status));
    }
}
