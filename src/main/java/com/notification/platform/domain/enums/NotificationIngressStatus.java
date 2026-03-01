package com.notification.platform.domain.enums;

import lombok.Getter;

@Getter
public enum NotificationIngressStatus {
    /** Accepted for processing */
    ACCEPTED,
    /** Duplicate request detected */
    DUPLICATE;
}
