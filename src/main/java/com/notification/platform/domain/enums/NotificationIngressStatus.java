package com.notification.platform.domain.enums;

import lombok.Getter;

@Getter
public enum NotificationIngressStatus {
    /** Accepted for processing */
    ACCEPTED,
    /** Successfully dispatched to Kafka */
    DISPATCHED,
    /** Failed to dispatch to Kafka */
    FAILED,
    /** Duplicate request detected */
    DUPLICATE;
}
