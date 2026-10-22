package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.IndexingStatus;
import com.workpilot.ai.domain.ProcessingStatus;
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
        String storageKey,
        ProcessingStatus processingStatus,
        String processingError,
        IndexingStatus indexingStatus,
        String indexingError,
        boolean textIndexed,
        String textPreview,
        UUID uploadedById,
        String uploadedByName,
        Instant createdAt,
        Instant updatedAt
) {
    private static final int PREVIEW_LENGTH = 500;
    private static final String STALE = "This document was indexed with a different embedding model.";

    public static DocumentResponse fromEntity(Document document) {
        return fromEntity(document, null, null, null);
    }

    public static DocumentResponse fromEntity(Document document, String model, String version, Integer dimensions) {
        User uploader = document.getUploadedBy();
        String preview = preview(document.getExtractedText());
        IndexingStatus indexingStatus = visibleStatus(document, model, version, dimensions);
        String indexingError = indexingStatus == IndexingStatus.STALE ? STALE : document.getIndexingError();
        return new DocumentResponse(
                document.getId(),
                document.scopedOrganizationId(),
                document.getTitle(),
                document.getDescription(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStorageKey(),
                document.getProcessingStatus(),
                document.getProcessingError(),
                indexingStatus,
                indexingError,
                preview != null,
                preview,
                uploader.getId(),
                uploader.getFullName(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    static IndexingStatus visibleStatus(Document document, String model, String version, Integer dimensions) {
        if (document.getIndexingStatus() != IndexingStatus.COMPLETED || model == null || version == null || dimensions == null) {
            return document.getIndexingStatus();
        }
        boolean sameModel = model.equals(document.getIndexedModel())
                && version.equals(document.getIndexedModelVersion())
                && dimensions.equals(document.getIndexedDimensions());
        return sameModel ? IndexingStatus.COMPLETED : IndexingStatus.STALE;
    }

    private static String preview(String extracted) {
        if (extracted == null || extracted.isBlank()) {
            return null;
        }
        String cleaned = extracted.replace('\f', ' ').trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= PREVIEW_LENGTH ? cleaned : cleaned.substring(0, PREVIEW_LENGTH);
    }
}
