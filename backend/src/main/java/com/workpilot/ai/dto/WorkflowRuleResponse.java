package com.workpilot.ai.dto;

import com.workpilot.ai.domain.WorkflowActionType;
import com.workpilot.ai.domain.WorkflowCondition;
import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.domain.WorkflowRule;

import java.time.Instant;
import java.util.UUID;

public record WorkflowRuleResponse(
        UUID id,
        String name,
        WorkflowEventType eventType,
        WorkflowCondition conditionType,
        WorkflowActionType actionType,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowRuleResponse fromEntity(WorkflowRule rule) {
        return new WorkflowRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getEventType(),
                rule.getConditionType(),
                rule.getActionType(),
                rule.isEnabled(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
