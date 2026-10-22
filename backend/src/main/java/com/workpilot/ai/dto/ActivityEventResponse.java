package com.workpilot.ai.dto;

import com.workpilot.ai.domain.ActivityEvent;
import com.workpilot.ai.domain.ActivityType;

import java.time.Instant;
import java.util.UUID;

public record ActivityEventResponse(
        UUID id,
        ActivityType type,
        String summary,
        UUID taskId,
        UUID actorId,
        String actorName,
        Instant occurredAt
) {
    public static ActivityEventResponse fromEntity(ActivityEvent event) {
        return new ActivityEventResponse(
                event.getId(),
                event.getType(),
                event.getSummary(),
                event.getTaskId(),
                event.getActor().getId(),
                event.getActor().getFullName(),
                event.getOccurredAt()
        );
    }
}
