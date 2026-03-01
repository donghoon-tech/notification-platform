package com.notification.platform.dispatcher;

import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final DispatcherService dispatcherService;

    @KafkaListener(topics = "${spring.kafka.topic.inbound}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(NotificationRequestEvent event) {
        log.info("Consumed notification request event: {}", event.getRequestId());
        try {
            dispatcherService.dispatch(event);
        } catch (Exception e) {
            log.error("Error processing notification request: {}", e.getMessage(), e);
            // In a real system, we might send this to a DLQ (Dead Letter Queue)
        }
    }
}
