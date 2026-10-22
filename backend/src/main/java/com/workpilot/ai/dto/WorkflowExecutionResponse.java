package com.workpilot.ai.dto;

import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.domain.WorkflowExecution;
import com.workpilot.ai.domain.WorkflowExecutionStatus;

import java.time.Instant;
import java.util.UUID;

public record WorkflowExecutionResponse(
        UUID id,
        UUID ruleId,
        String ruleName,
        WorkflowEventType eventType,
        WorkflowExecutionStatus status,
        int attemptCount,
        String errorMessage,
        String resultMessage,
        Instant createdAt,
        Instant finishedAt
) {
    public static WorkflowExecutionResponse fromEntity(WorkflowExecution execution) {
        return new WorkflowExecutionResponse(
                execution.getId(),
                execution.getRule().getId(),
                execution.getRule().getName(),
                execution.getEventType(),
                execution.getStatus(),
                execution.getAttemptCount(),
                execution.getErrorMessage(),
                execution.getResultMessage(),
                execution.getCreatedAt(),
                execution.getFinishedAt());
    }
}
