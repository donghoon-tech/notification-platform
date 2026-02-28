package com.notification.platform.domain.repository;

import com.notification.platform.domain.entity.NotificationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, UUID> {
}
