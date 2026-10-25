package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.TaskDocumentLink;

import java.time.Instant;
import java.util.UUID;

public record TaskDocumentLinkResponse(
        UUID id,
        UUID taskId,
        UUID organizationId,
        UUID documentId,
        String title,
        String originalFileName,
        String contentType,
        UUID attachedById,
        String attachedByName,
        Instant createdAt
) {
    public static TaskDocumentLinkResponse fromEntity(TaskDocumentLink link) {
        Document document = link.getDocument();
        return new TaskDocumentLinkResponse(
                link.getId(),
                link.getTask().getId(),
                link.scopedOrganizationId(),
                document.getId(),
                document.getTitle(),
                document.getOriginalFileName(),
                document.getContentType(),
                link.getAttachedBy().getId(),
                link.getAttachedBy().getFullName(),
                link.getCreatedAt());
    }
}
