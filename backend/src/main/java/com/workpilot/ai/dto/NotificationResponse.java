package com.workpilot.ai.dto;

import com.workpilot.ai.domain.AppNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String title,
        String body,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse fromEntity(AppNotification notification, String organizationName) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrganizationId(),
                organizationName,
                notification.getTitle(),
                notification.getBody(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
