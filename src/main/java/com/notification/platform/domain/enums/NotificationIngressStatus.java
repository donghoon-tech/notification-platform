package com.notification.platform.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationIngressStatus {
    ACCEPTED("Accepted"),
    DUPLICATE("Duplicate request");

    private final String description;
}
