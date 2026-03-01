package com.notification.platform.service;

import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRequestRepository repository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("Service saves notification request and publishes to Kafka")
    void triggerNotification_SavesAndPublishes() {
        // Given
        NotificationSendRequest request = NotificationSendRequest.builder()
                .idempotencyKey("test-key-123")
                .producerName("ORDER_SERVICE")
                .recipientId("user-789")
                .channel("IN_APP")
                .payload(Map.of("message", "Hello World"))
                .build();

        // When
        NotificationSendResponse response = notificationService.triggerNotification(request);

        // Then
        assertThat(response.getRequestId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(NotificationIngressStatus.ACCEPTED);
        verify(repository, times(1)).save(any(NotificationRequest.class));
        verify(kafkaTemplate, times(1)).send(eq("notification.requests"), eq("user-789"), any());
    }
}
