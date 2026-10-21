package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.User;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        String originalFileName,
        String contentType,
        long sizeBytes,
        boolean textIndexed,
        String textPreview,
        UUID uploadedById,
        String uploadedByName,
        Instant createdAt,
        Instant updatedAt
) {
    private static final int PREVIEW_LENGTH = 500;

    public static DocumentResponse fromEntity(Document document) {
        User uploader = document.getUploadedBy();
        String extracted = document.getExtractedText();
        String preview = null;
        if (extracted != null && !extracted.isBlank()) {
            preview = extracted.length() <= PREVIEW_LENGTH ? extracted : extracted.substring(0, PREVIEW_LENGTH);
        }
        return new DocumentResponse(
                document.getId(),
                document.scopedOrganizationId(),
                document.getTitle(),
                document.getDescription(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                preview != null,
                preview,
                uploader.getId(),
                uploader.getFullName(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
