package com.notification.platform.domain.enums;

import lombok.Getter;

@Getter
public enum DeliveryStatus {
    /** Waiting for processing */
    PENDING,
    /** Published to Kafka topic */
    QUEUED,
    /** Dispatched to actual channel provider */
    DISPATCHED,
    /** Successfully delivered to the end user */
    DELIVERED,
    /** Failed to deliver */
    FAILED;
}
