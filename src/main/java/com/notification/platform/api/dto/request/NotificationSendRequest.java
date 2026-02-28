package com.notification.platform.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationSendRequest {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotBlank(message = "Producer name is required")
    private String producerName;

    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    @NotBlank(message = "Channel is required")
    private String channel; // e.g., IN_APP, EMAIL

    private String targetAddress; // Optional: email address or device token

    @Builder.Default
    private String priority = "NORMAL"; // HIGH, NORMAL

    @NotEmpty(message = "Payload cannot be empty")
    private Map<String, Object> payload;
}
