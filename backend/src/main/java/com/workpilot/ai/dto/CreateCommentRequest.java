package com.workpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Enter a comment.")
        @Size(max = 2000, message = "Comment must be at most 2000 characters")
        String body
) {
}
