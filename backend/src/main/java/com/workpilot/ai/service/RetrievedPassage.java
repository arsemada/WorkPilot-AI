package com.workpilot.ai.service;

import java.util.UUID;

public record RetrievedPassage(
        UUID documentId,
        String documentTitle,
        int chunkIndex,
        Integer pageNumber,
        String sectionLabel,
        String content,
        double distance
) {
}
