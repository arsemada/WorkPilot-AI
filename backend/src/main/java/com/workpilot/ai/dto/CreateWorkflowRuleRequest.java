package com.workpilot.ai.dto;

import com.workpilot.ai.domain.WorkflowActionType;
import com.workpilot.ai.domain.WorkflowEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkflowRuleRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,
        @NotNull(message = "Event type is required")
        WorkflowEventType eventType,
        @NotNull(message = "Action type is required")
        WorkflowActionType actionType
) {
}
