package com.notification.platform.api.dto.request;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRetryRequest {
    private String targetAddress; // Optional override
}
