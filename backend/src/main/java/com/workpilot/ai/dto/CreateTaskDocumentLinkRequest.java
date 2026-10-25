package com.workpilot.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTaskDocumentLinkRequest(
        @NotNull(message = "Choose a document.")
        UUID documentId
) {
}
