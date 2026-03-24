package com.notification.platform.service;

import com.notification.platform.api.dto.request.AdminRetryRequest;
import com.notification.platform.config.SnowflakeIdGenerator;
import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final NotificationRequestRepository requestRepository;
    private final DeliveryLogRepository logRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private static final String RETRY_LOCK_PREFIX = "retry_lock:";
    private static final String TOPIC = "notification.requests";

    @Transactional
    public void triggerRetry(Long requestId, AdminRetryRequest retryRequest) {
        log.info("Admin manual retry triggered for request: {}", requestId);

        // 1. Idempotency Check (Redis lock)
        String lockKey = RETRY_LOCK_PREFIX + requestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Retry already in progress for request: {}", requestId);
            throw new IllegalStateException("Retry already in progress for request: " + requestId);
        }

        // 2. Fetch original request
        NotificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        // 3. Create new DeliveryLog
        String targetAddress = (retryRequest != null && retryRequest.getTargetAddress() != null)
                ? retryRequest.getTargetAddress()
                : request.getTargetAddress();

        DeliveryLog retryLog = DeliveryLog.builder()
                .id(snowflakeIdGenerator.nextId())
                .request(request)
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .targetAddress(targetAddress)
                .status(DeliveryStatus.RETRY_PENDING)
                .errorMessage("Manual retry triggered by Admin")
                .build();
        
        logRepository.save(retryLog);

        // 4. Re-publish to Kafka
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(request.getId())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .targetAddress(targetAddress)
                .priority(request.getPriority())
                .payload(request.getPayload())
                .build();

        try {
            kafkaTemplate.send(TOPIC, request.getRecipientId(), event);
            log.info("Manual retry re-injected to Kafka for request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to re-inject manual retry for request {}: {}", requestId, e.getMessage());
            // Optionally remove the lock if send fails immediately
            redisTemplate.delete(lockKey);
            throw new RuntimeException("Kafka dispatch failed during manual retry", e);
        }
    }
}
