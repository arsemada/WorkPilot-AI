package com.workpilot.ai.repository;

import com.workpilot.ai.domain.AppNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<AppNotification, UUID> {

    @Query("""
            SELECT notification FROM AppNotification notification
            WHERE notification.userId = :userId
            ORDER BY notification.createdAt DESC
            """)
    List<AppNotification> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByExecutionIdAndUserId(UUID executionId, UUID userId);

    Optional<AppNotification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("""
            UPDATE AppNotification notification
            SET notification.readAt = :readAt
            WHERE notification.userId = :userId
              AND notification.readAt IS NULL
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
