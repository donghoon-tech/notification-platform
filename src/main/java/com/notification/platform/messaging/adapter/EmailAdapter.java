package com.notification.platform.messaging.adapter;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAdapter {

    private final JavaMailSender mailSender;
    private final DeliveryLogRepository deliveryLogRepository;

    @Transactional
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
            log.error("Failed to send email: {}", e.getMessage(), e);
            updateDeliveryStatus(event.getRequestId(), DeliveryStatus.FAILED);
        }
    }

    private void updateDeliveryStatus(java.util.UUID requestId, DeliveryStatus status) {
        deliveryLogRepository.findByRequestIdAndChannel(requestId, NotificationChannel.EMAIL)
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
                    log.info("Email DeliveryLog updated to {} for request: {}", status, requestId);
                });
    }
}
