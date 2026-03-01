package com.notification.platform.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.platform.api.dto.request.NotificationSendRequest;
import com.notification.platform.api.dto.response.NotificationSendResponse;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("Notification send request succeeds and returns 200 OK")
    void sendNotification_Success() throws Exception {
        // Given
        NotificationSendRequest request = NotificationSendRequest.builder()
                .idempotencyKey("test-key-123")
                .producerName("ORDER_SERVICE")
                .recipientId("user-789")
                .channel(NotificationChannel.IN_APP)
                .payload(Map.of("message", "Hello World"))
                .build();

        UUID expectedId = UUID.randomUUID();
        given(notificationService.triggerNotification(any(NotificationSendRequest.class)))
                .willReturn(NotificationSendResponse.builder()
                        .requestId(expectedId)
                        .status(NotificationIngressStatus.ACCEPTED)
                        .build());

        // When & Then
        mockMvc.perform(post("/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(expectedId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("Notification send request fails with 400 Bad Request when validation fails")
    void sendNotification_BadRequest() throws Exception {
        // Given: Missing required fields
        NotificationSendRequest request = NotificationSendRequest.builder()
                .producerName("") // Invalid: Blank
                .build();

        // When & Then
        mockMvc.perform(post("/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
