package com.notification.platform.domain.repository;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {
    Optional<DeliveryLog> findByRequestIdAndChannel(UUID requestId, NotificationChannel channel);
}
