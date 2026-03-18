package com.notification.platform.messaging.event;

import com.notification.platform.domain.enums.NotificationChannel;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class NotificationRequestCreatedEvent {
    private final Long requestId;
    private final String recipientId;
    private final NotificationChannel channel;
    private final String targetAddress;
    private final String priority;
    private final Map<String, Object> payload;
}
