package com.notification.platform.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryStatus {
    PENDING("Waiting for processing"),
    QUEUED("Published to Kafka"),
    DISPATCHED("Dispatched to channel"),
    DELIVERED("Successfully delivered"),
    FAILED("Delivery failed");

    private final String description;
}
