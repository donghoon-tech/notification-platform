package com.notification.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditConfigTest {

    private final JpaAuditConfig jpaAuditConfig = new JpaAuditConfig();

    @Test
    @DisplayName("DateTimeProvider should provide OffsetDateTime")
    void offsetDateTimeProvider_ReturnsOffsetDateTime() {
        // Given
        DateTimeProvider provider = jpaAuditConfig.offsetDateTimeProvider();

        // When
        Optional<TemporalAccessor> now = provider.getNow();

        // Then
        assertThat(now).isPresent();
        assertThat(now.get()).isInstanceOf(OffsetDateTime.class);
    }
}
