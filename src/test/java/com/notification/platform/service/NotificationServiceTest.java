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
import org.mockito.ArgumentCaptor;
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
    @DisplayName("Process new request and save with ACCEPTED status (FR-24)")
    void triggerNotification_SavesWithAcceptedStatus() {
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
        notificationService.triggerNotification(request);

        // Then: FR-24 - Initial status must be ACCEPTED
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationIngressStatus.ACCEPTED);
        
        verify(eventPublisher, times(1)).publishEvent(any(NotificationRequestCreatedEvent.class));
    }

    @Test
    @DisplayName("Return existing requestId when duplicate detected")
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
}
