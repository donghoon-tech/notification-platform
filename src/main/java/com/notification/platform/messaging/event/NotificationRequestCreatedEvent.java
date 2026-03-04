package com.notification.platform.messaging.event;

import com.notification.platform.domain.enums.NotificationChannel;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class NotificationRequestCreatedEvent {
    private final UUID requestId;
    private final String recipientId;
    private final NotificationChannel channel;
    private final String targetAddress;
    private final String priority;
    private final Map<String, Object> payload;
}
