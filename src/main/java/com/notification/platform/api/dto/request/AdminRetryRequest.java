package com.notification.platform.api.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
public class AdminRetryRequest {
    private String targetAddress; // Optional override
}
