package com.notification.platform.domain.repository;

import com.notification.platform.domain.entity.DeliveryLog;
import com.notification.platform.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {
    Optional<DeliveryLog> findByRequestIdAndChannel(Long requestId, NotificationChannel channel);
}
