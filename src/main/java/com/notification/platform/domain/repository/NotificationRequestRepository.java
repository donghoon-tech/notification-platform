package com.notification.platform.domain.repository;

import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, UUID> {
    Optional<NotificationRequest> findByIdempotencyKey(String idempotencyKey);

    Page<NotificationRequest> findAllByStatusAndRequestedAtBefore(
            NotificationIngressStatus status, 
            OffsetDateTime requestedAt, 
            Pageable pageable
    );
}
