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
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRequestRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final com.notification.platform.config.SnowflakeIdGenerator snowflakeIdGenerator;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    @Transactional
    public NotificationSendResponse triggerNotification(NotificationSendRequest request) {
        String idempotencyKey = request.getIdempotencyKey();

        // 1. Check Redis for idempotency
        String cachedRequestId = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
        if (cachedRequestId != null) {
            log.info("Duplicate request detected in cache for key: {}", idempotencyKey);
            return NotificationSendResponse.builder()
                    .requestId(Long.parseLong(cachedRequestId))
                    .status(NotificationIngressStatus.ACCEPTED)
                    .build();
        }

        // 2. Check DB and update cache (Fallback)
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
                .id(snowflakeIdGenerator.nextId())
                .idempotencyKey(request.getIdempotencyKey())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .targetAddress(request.getTargetAddress())
                .producerName(request.getProducerName())
                .priority(request.getPriority())
                .payload(request.getPayload())
                .status(NotificationIngressStatus.ACCEPTED) // FR-24: Initial status
                .requestedAt(OffsetDateTime.now())
                .build();

        repository.save(notificationRequest);
        redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + request.getIdempotencyKey(), notificationRequest.getId().toString(), IDEMPOTENCY_TTL);

        // ADR-01: Publish local event for post-commit dispatch
        NotificationRequestCreatedEvent localEvent = NotificationRequestCreatedEvent.builder()
                .requestId(notificationRequest.getId())
                .recipientId(request.getRecipientId())
                .channel(request.getChannel())
                .targetAddress(request.getTargetAddress())
                .priority(request.getPriority())
                .payload(request.getPayload())
                .build();

        eventPublisher.publishEvent(localEvent);
        log.info("Notification request persisted and event published: {}", notificationRequest.getId());

        return NotificationSendResponse.builder()
                .requestId(notificationRequest.getId())
                .status(NotificationIngressStatus.ACCEPTED)
                .build();
    }
}
