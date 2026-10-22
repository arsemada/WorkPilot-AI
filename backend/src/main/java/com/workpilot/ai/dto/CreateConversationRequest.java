package com.workpilot.ai.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 120, message = "Title must be at most 120 characters")
        String title
) {
}
