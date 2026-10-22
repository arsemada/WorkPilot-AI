package com.workpilot.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.workpilot.ai.domain.ToolCallStatus;

import java.util.UUID;

public record AssistantToolCallResponse(
        UUID id,
        String name,
        ToolCallStatus status,
        JsonNode arguments,
        JsonNode result,
        String errorMessage
) {
}
