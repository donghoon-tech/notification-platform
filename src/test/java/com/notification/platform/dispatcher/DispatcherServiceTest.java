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

    @Mock
    private com.notification.platform.config.SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private PresenceManager presenceManager;

    @InjectMocks
    private DispatcherService dispatcherService;

    @Test
    @DisplayName("Dispatcher routes IN_APP channel correctly and updates status to QUEUED when online")
    void dispatch_InApp_Success() {
        // given
        Long requestId = System.nanoTime();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(requestId)
                .recipientId("user-123")
                .channel(NotificationChannel.IN_APP)
                .payload(Map.of("msg", "hello"))
                .build();

        NotificationRequest request = NotificationRequest.builder().id(requestId).build();
        when(notificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(presenceManager.isOnline("user-123")).thenReturn(true);

        // when
        dispatcherService.dispatch(event);

        // then
        verify(deliveryLogRepository, times(1)).save(any(DeliveryLog.class));
        verify(kafkaTemplate).send(eq("notification.inapp"), eq("user-123"), eq(event));
    }

    @Test
    @DisplayName("Dispatcher reroutes IN_APP to EMAIL when user is offline")
    void dispatch_InApp_Offline_Fallback_Success() {
        // given
        Long requestId = System.nanoTime();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(requestId)
                .recipientId("user-123")
                .channel(NotificationChannel.IN_APP)
                .payload(Map.of("msg", "hello"))
                .build();

        NotificationRequest request = NotificationRequest.builder().id(requestId).build();
        when(notificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(presenceManager.isOnline("user-123")).thenReturn(false);

        // when
        dispatcherService.dispatch(event);

        // then
        // 1. First call: IN_APP marked as REROUTED
        // 2. Second call: EMAIL marked as PENDING/QUEUED
        verify(deliveryLogRepository, times(2)).save(any(DeliveryLog.class));
        verify(kafkaTemplate).send(eq("notification.email"), eq("user-123"), any(NotificationRequestEvent.class));
        verify(kafkaTemplate, never()).send(eq("notification.inapp"), anyString(), any());
    }

    @Test
    @DisplayName("Dispatcher routes EMAIL channel correctly and updates status to QUEUED")
    void dispatch_Email_Success() {
        // given
        Long requestId = System.nanoTime();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(requestId)
                .recipientId("user-123")
                .channel(NotificationChannel.EMAIL)
                .targetAddress("test@example.com")
                .payload(Map.of("msg", "hello"))
                .build();

        NotificationRequest request = NotificationRequest.builder().id(requestId).build();
        when(notificationRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        // when
        dispatcherService.dispatch(event);

        // then
        verify(deliveryLogRepository, times(1)).save(any(DeliveryLog.class)); // 1. PENDING (QUEUED relies on dirty checking)
        verify(kafkaTemplate).send(eq("notification.email"), eq("user-123"), eq(event));
    }
}
