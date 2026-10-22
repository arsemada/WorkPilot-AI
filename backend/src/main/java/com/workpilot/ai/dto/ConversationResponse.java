package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID organizationId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConversationResponse fromEntity(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.scopedOrganizationId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
