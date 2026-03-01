package com.notification.platform.domain.entity;

import com.notification.platform.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeliveryLog {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private NotificationRequest request;

    @Column(nullable = false)
    private String recipientId;

    @Column(nullable = false)
    private String channel;

    private String targetAddress;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private String errorMessage;

    @CreatedDate
    @Column(updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    private OffsetDateTime updatedAt;
}
