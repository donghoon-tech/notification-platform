package com.notification.platform.messaging.adapter;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAdapter {

    private final JavaMailSender mailSender;
    private final DeliveryLogRepository deliveryLogRepository;

    @Transactional
    @RetryableTopic(
            attempts = "4", // Initial try + 3 retries
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false", // We will create topics explicitly or let Kafka auto-create based on broker config
            dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = "${spring.kafka.topic.email}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(NotificationRequestEvent event) {
        log.info("EmailAdapter consumed event for request: {}", event.getRequestId());

        try {
            // 1. Send Email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getTargetAddress());
            message.setSubject("Notification from Platform");
            message.setText(event.getPayload().getOrDefault("message", "No content").toString());
            
            mailSender.send(message);
            log.info("Email sent to: {}", event.getTargetAddress());

            // 2. Tracking: Update DeliveryLog status
            updateDeliveryStatus(event.getRequestId(), DeliveryStatus.DELIVERED);

        } catch (Exception e) {
            log.error("Failed to send email. Will be retried by Spring Kafka. Error: {}", e.getMessage());
            throw new RuntimeException("Email delivery failed", e);
        }
    }

    @DltHandler
    public void handleDlt(NotificationRequestEvent event, @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {
        log.error("Message for request {} routed to DLQ. Final error: {}", event.getRequestId(), errorMessage);
        
        // Update DB status to FAILED so users/clients can see it.
        updateDeliveryStatus(event.getRequestId(), DeliveryStatus.FAILED);
        
        // TODO (v4.0): The message is now safely resting in the DLQ topic (notification.email.dlq).
        // Implement an Admin API in v4.0 to manually consume and replay these dead letters.
    }

    private void updateDeliveryStatus(Long requestId, DeliveryStatus status) {
        deliveryLogRepository.findByRequestIdAndChannel(requestId, NotificationChannel.EMAIL)
                .ifPresent(deliveryLog -> {
                    deliveryLog.updateStatus(status);
                    log.info("Email DeliveryLog updated to {} for request: {}", status, requestId);
                });
    }
}
