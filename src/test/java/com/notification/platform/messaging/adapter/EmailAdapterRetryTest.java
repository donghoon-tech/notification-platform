package com.notification.platform.messaging.adapter;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.topic.email=notification.email",
        "spring.kafka.consumer.group-id=test-group",
        "management.health.mail.enabled=false",
        "spring.kafka.listener.auto-startup=true"
})
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" }, topics = { "notification.email" })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailAdapterRetryTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private DeliveryLogRepository deliveryLogRepository;

    @Test
    @DisplayName("Should retry 3 times and then route to DLQ on failure")
    void shouldRetryThreeTimesAndThenRouteToDlqOnFailure() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .requestId(requestId)
                .recipientId("user-123")
                .channel(NotificationChannel.EMAIL)
                .targetAddress("test@example.com")
                .payload(Map.of("message", "Test content"))
                .build();

        NotificationRequest mockRequest = NotificationRequest.builder()
                .id(requestId)
                .build();

        DeliveryLog mockLog = DeliveryLog.builder()
                .id(UUID.randomUUID())
                .request(mockRequest)
                .channel(NotificationChannel.EMAIL)
                .build();

        when(deliveryLogRepository.findByRequestIdAndChannel(requestId, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(mockLog));

        // Mock mailSender to always throw an exception
        doThrow(new RuntimeException("Simulated mail server down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // when
        kafkaTemplate.send("notification.email", event.getRecipientId(), event);

        // then
        // 1. Verify that mailSender.send() was called exactly 4 times (1 initial + 3 retries)
        // Awaitility handles the asynchronous nature of the delays (1s -> 2s -> 4s)
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(mailSender, times(4)).send(any(SimpleMailMessage.class));
        });

        // 2. Verify that after exhausting retries, DltHandler is invoked and status is set to FAILED
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(DeliveryStatus.FAILED, mockLog.getStatus());
        });
    }
}
