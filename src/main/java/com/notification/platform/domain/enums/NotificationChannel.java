package com.notification.platform.domain.enums;

import lombok.Getter;

@Getter
public enum NotificationChannel {
    /** In-app notification (WebSocket) */
    IN_APP,
    /** Email notification (SMTP) */
    EMAIL;
}
