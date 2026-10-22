package com.workpilot.ai.dto;

import java.util.UUID;

public record MessageSourceResponse(
        UUID documentId,
        String documentTitle,
        int chunkIndex,
        Integer pageNumber,
        String sectionLabel,
        String snippet
) {
}
