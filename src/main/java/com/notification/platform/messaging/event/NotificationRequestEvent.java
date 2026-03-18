package com.notification.platform.messaging.event;

import com.notification.platform.domain.enums.NotificationChannel;
import lombok.*;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationRequestEvent {

    private Long requestId;
    private String recipientId;
    private NotificationChannel channel;
    private String targetAddress;
    private String priority;
    private Map<String, Object> payload;

}
