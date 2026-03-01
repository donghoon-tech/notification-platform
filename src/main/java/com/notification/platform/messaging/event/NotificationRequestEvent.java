package com.notification.platform.messaging.event;

import com.notification.platform.domain.enums.NotificationChannel;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationRequestEvent {

    private UUID requestId;
    private String recipientId;
    private NotificationChannel channel;
    private String targetAddress;
    private String priority;
    private Map<String, Object> payload;

}
