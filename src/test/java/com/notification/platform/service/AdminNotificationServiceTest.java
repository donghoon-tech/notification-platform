package com.notification.platform.service;

import com.notification.platform.api.dto.request.AdminRetryRequest;
import com.notification.platform.config.SnowflakeIdGenerator;
import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock
    private NotificationRequestRepository requestRepository;
    @Mock
    private DeliveryLogRepository logRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private AdminNotificationService adminNotificationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        adminNotificationService = new AdminNotificationService(
                requestRepository, logRepository, kafkaTemplate, redisTemplate, snowflakeIdGenerator
        );
    }

    @Test
    @DisplayName("Should trigger retry and publish to Kafka with override address")
    void shouldTriggerRetryAndPublishToKafkaWithOverride() {
        // given
        Long requestId = 100L;
        String overrideAddress = "new@example.com";
        AdminRetryRequest retryRequest = AdminRetryRequest.builder()
                .targetAddress(overrideAddress)
                .build();

        NotificationRequest mockRequest = NotificationRequest.builder()
                .id(requestId)
                .recipientId("user-1")
                .channel(NotificationChannel.EMAIL)
                .targetAddress("old@example.com")
                .build();

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(mockRequest));
        when(snowflakeIdGenerator.nextId()).thenReturn(200L);

        // when
        adminNotificationService.triggerRetry(requestId, retryRequest);

        // then
        // 1. Verify DeliveryLog creation
        ArgumentCaptor<DeliveryLog> logCaptor = ArgumentCaptor.forClass(DeliveryLog.class);
        verify(logRepository).save(logCaptor.capture());
        DeliveryLog savedLog = logCaptor.getValue();
        assertEquals(200L, savedLog.getId());
        assertEquals(requestId, savedLog.getRequest().getId());
        assertEquals(DeliveryStatus.RETRY_PENDING, savedLog.getStatus());
        assertEquals(overrideAddress, savedLog.getTargetAddress());

        // 2. Verify Kafka publication
        ArgumentCaptor<NotificationRequestEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRequestEvent.class);
        verify(kafkaTemplate).send(eq("notification.requests"), eq("user-1"), eventCaptor.capture());
        NotificationRequestEvent publishedEvent = eventCaptor.getValue();
        assertEquals(requestId, publishedEvent.getRequestId());
        assertEquals(overrideAddress, publishedEvent.getTargetAddress());
    }

    @Test
    @DisplayName("Should throw exception if retry is already in progress")
    void shouldThrowExceptionIfRetryInProgress() {
        // given
        Long requestId = 100L;
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        // when & then
        assertThrows(IllegalStateException.class, () -> adminNotificationService.triggerRetry(requestId, null));
        verify(requestRepository, never()).findById(anyLong());
    }
}
