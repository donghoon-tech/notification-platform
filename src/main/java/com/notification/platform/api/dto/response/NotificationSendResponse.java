package com.notification.platform.api.dto.response;

import com.notification.platform.domain.enums.NotificationIngressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSendResponse {

    private UUID requestId;
    private NotificationIngressStatus status;

}
