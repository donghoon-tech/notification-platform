package com.notification.platform.service;

import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRequestRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Transactional
    public NotificationSendResponse triggerNotification(NotificationSendRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // 1. Check Redis for cached requestId
        String cachedRequestId = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
        if (cachedRequestId != null) {
            log.info("Duplicate request detected in cache for key: {}", idempotencyKey);
            return NotificationSendResponse.builder()
                    .requestId(UUID.fromString(cachedRequestId))
                    .status(NotificationIngressStatus.ACCEPTED)
                    .build();
        }

        // 2. Check DB and update cache if found (Fallback)
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    log.info("Duplicate request detected in DB for key: {}", idempotencyKey);
                    redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + idempotencyKey, existing.getId().toString(), IDEMPOTENCY_TTL);
                    return NotificationSendResponse.builder()
                            .requestId(existing.getId())
                            .status(NotificationIngressStatus.ACCEPTED)
                            .build();
                })
                .orElseGet(() -> createAndPublishEvent(request));
    }

    private NotificationSendResponse createAndPublishEvent(NotificationSendRequest request) {
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(request.getIdempotencyKey())
                .producerName(request.getProducerName())
                .priority(request.getPriority())
                .payload(request.getPayload())
                .build();

        repository.save(notificationRequest);
        redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + request.getIdempotencyKey(), notificationRequest.getId().toString(), IDEMPOTENCY_TTL);

        // Publish local event instead of direct Kafka sending to ensure transactional safety
        NotificationRequestCreatedEvent localEvent = NotificationRequestCreatedEvent.builder()
                .requestId(notificationRequest.getId())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .targetAddress(request.getTargetAddress())
                .priority(request.getPriority())
                .payload(request.getPayload())
                .build();

        eventPublisher.publishEvent(localEvent);
        log.info("Local event published for notification request: {}", notificationRequest.getId());

        return NotificationSendResponse.builder()
                .requestId(notificationRequest.getId())
                .status(NotificationIngressStatus.ACCEPTED)
                .build();
    }
}
