package com.workpilot.ai.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDetailResponse(
        UUID id,
        UUID organizationId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationMessageResponse> messages
) {
}
