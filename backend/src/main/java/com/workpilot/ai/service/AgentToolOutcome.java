package com.workpilot.ai.service;

import com.workpilot.ai.domain.ToolCallStatus;

import java.util.UUID;

public record AgentToolOutcome(
        UUID id,
        String name,
        String argumentsJson,
        ToolCallStatus status,
        String resultJson,
        String errorMessage
) {
}
