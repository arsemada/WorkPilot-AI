package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.ProcessingStatus;

import java.util.UUID;

public record DocumentStatusResponse(
        UUID id,
        ProcessingStatus processingStatus,
        String processingError,
        boolean textIndexed
) {
    public static DocumentStatusResponse fromEntity(Document document) {
        String extracted = document.getExtractedText();
        return new DocumentStatusResponse(
                document.getId(),
                document.getProcessingStatus(),
                document.getProcessingError(),
                extracted != null && !extracted.isBlank()
        );
    }
}
