package com.notification.platform.dispatcher;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatcherServiceTest {

    @Mock
    private DeliveryLogRepository deliveryLogRepository;

    @Mock
    private NotificationRequestRepository notificationRequestRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private DispatcherService dispatcherService;

    @Test
    @DisplayName("Dispatcher routes IN_APP channel correctly and updates status to QUEUED")
    void dispatch_InApp_Success() {
        // Given
        UUID requestId = UUID.randomUUID();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(requestId)
                .recipientId("user-123")
                .channel(NotificationChannel.IN_APP)
                .payload(Map.of("msg", "hello"))
                .build();

        NotificationRequest request = NotificationRequest.builder().id(requestId).build();
        when(notificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        // When
        dispatcherService.dispatch(event);

        // Then
        verify(deliveryLogRepository, times(2)).save(any(DeliveryLog.class)); // 1. PENDING, 2. QUEUED
        verify(kafkaTemplate).send(eq("notification.inapp"), eq("user-123"), eq(event));
    }

    @Test
    @DisplayName("Dispatcher routes EMAIL channel correctly and updates status to QUEUED")
    void dispatch_Email_Success() {
        // Given
        UUID requestId = UUID.randomUUID();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(requestId)
                .recipientId("user-123")
                .channel(NotificationChannel.EMAIL)
                .targetAddress("test@example.com")
                .payload(Map.of("msg", "hello"))
                .build();

        NotificationRequest request = NotificationRequest.builder().id(requestId).build();
        when(notificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        // When
        dispatcherService.dispatch(event);

        // Then
        verify(deliveryLogRepository, times(2)).save(any(DeliveryLog.class));
        verify(kafkaTemplate).send(eq("notification.email"), eq("user-123"), eq(event));
    }
}
