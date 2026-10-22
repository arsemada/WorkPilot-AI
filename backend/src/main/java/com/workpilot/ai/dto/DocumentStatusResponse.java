package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.IndexingStatus;
import com.workpilot.ai.domain.ProcessingStatus;

import java.util.UUID;

public record DocumentStatusResponse(
        UUID id,
        ProcessingStatus processingStatus,
        String processingError,
        IndexingStatus indexingStatus,
        String indexingError,
        boolean textIndexed
) {
    public static DocumentStatusResponse fromEntity(Document document, String model, String version, Integer dimensions) {
        DocumentResponse presented = DocumentResponse.fromEntity(document, model, version, dimensions);
        return new DocumentStatusResponse(
                document.getId(),
                document.getProcessingStatus(),
                document.getProcessingError(),
                presented.indexingStatus(),
                presented.indexingError(),
                presented.textIndexed()
        );
    }
}
