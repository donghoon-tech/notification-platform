package com.notification.platform.service;

import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Return existing requestId when duplicate detected in Redis")
    void triggerNotification_DuplicateInRedis() {
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
        verify(repository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Process and publish new request when no duplicate found")
    void triggerNotification_NewRequest() {
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
        verify(valueOperations, times(1)).set(eq("idempotency:new-key"), anyString(), any());
        verify(kafkaTemplate, times(1)).send(eq("notification.requests"), eq("user-123"), any());
    }
}
