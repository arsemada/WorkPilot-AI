package com.workpilot.ai.dto;

import com.workpilot.ai.domain.ConversationMessage;
import com.workpilot.ai.domain.MessageRole;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageResponse(
        UUID id,
        MessageRole role,
        String content,
        Instant createdAt
) {
    public static ConversationMessageResponse fromEntity(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
