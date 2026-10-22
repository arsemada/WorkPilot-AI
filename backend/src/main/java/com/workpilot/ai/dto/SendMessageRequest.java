package com.workpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must be 4000 characters or fewer")
        String content
) {
}
