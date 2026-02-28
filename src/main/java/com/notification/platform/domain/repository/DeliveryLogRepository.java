package com.notification.platform.domain.repository;

import com.notification.platform.domain.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {
}
