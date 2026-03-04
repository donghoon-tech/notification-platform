package com.notification.platform.service;

import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRequestRepository repository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Detect duplicate request and skip event publishing")
    void triggerNotification_DuplicateDetected() {
        // Given
        UUID existingId = UUID.randomUUID();
        NotificationSendRequest request = NotificationSendRequest.builder()
                .idempotencyKey("duplicate-key")
                .build();

        given(valueOperations.get("idempotency:duplicate-key")).willReturn(existingId.toString());

        // When
        NotificationSendResponse response = notificationService.triggerNotification(request);

        // Then
        assertThat(response.getRequestId()).isEqualTo(existingId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Process new request and publish local event")
    void triggerNotification_NewRequestPublished() {
        // Given
        NotificationSendRequest request = NotificationSendRequest.builder()
                .idempotencyKey("new-key")
                .producerName("TEST_SERVICE")
                .recipientId("user-123")
                .channel(NotificationChannel.EMAIL)
                .targetAddress("test@test.com")
                .payload(Map.of("message", "hello"))
                .priority("NORMAL")
                .build();

        given(valueOperations.get(anyString())).willReturn(null);
        given(repository.findByIdempotencyKey(anyString())).willReturn(Optional.empty());

        // When
        NotificationSendResponse response = notificationService.triggerNotification(request);

        // Then
        assertThat(response.getRequestId()).isNotNull();
        verify(repository, times(1)).save(any(NotificationRequest.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestCreatedEvent.class));
    }
}
