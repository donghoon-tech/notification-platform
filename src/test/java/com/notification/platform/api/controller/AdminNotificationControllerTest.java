package com.notification.platform.api.controller;

import com.notification.platform.api.dto.request.AdminRetryRequest;
import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.DeliveryStatus;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.DeliveryLogRepository;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"notification.requests"})
@DirtiesContext
class AdminNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRequestRepository requestRepository;

    @Autowired
    private DeliveryLogRepository deliveryLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("Should accept retry request and create new DeliveryLog")
    void shouldAcceptRetryRequest() throws Exception {
        // given
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(java.time.Duration.class)))
                .thenReturn(true);

        NotificationRequest request = NotificationRequest.builder()
                .id(12345L)
                .recipientId("user-123")
                .channel(NotificationChannel.EMAIL)
                .targetAddress("old@example.com")
                .payload(null)
                .producerName("test-producer")
                .priority("HIGH")
                .status(NotificationIngressStatus.ACCEPTED)
                .requestedAt(java.time.OffsetDateTime.now())
                .build();
        requestRepository.save(request);

        AdminRetryRequest retryRequest = AdminRetryRequest.builder()
                .targetAddress("new@example.com")
                .build();

        // when
        mockMvc.perform(post("/v1/admin/notifications/{requestId}/retry", request.getId())
                        .header("X-API-KEY", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retryRequest)))
                .andExpect(status().isAccepted());

        // then
        boolean logExists = deliveryLogRepository.findAll().stream()
                .anyMatch(log -> log.getRequest().getId().equals(request.getId()) 
                        && log.getStatus() == DeliveryStatus.RETRY_PENDING
                        && "new@example.com".equals(log.getTargetAddress()));
        
        assertTrue(logExists, "DeliveryLog with RETRY_PENDING status should be created");
    }
}
