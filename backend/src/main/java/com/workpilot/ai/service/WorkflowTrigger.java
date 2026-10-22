package com.workpilot.ai.service;

import com.workpilot.ai.domain.WorkflowEventType;

import java.time.LocalDate;
import java.util.UUID;

public record WorkflowTrigger(
        UUID organizationId,
        WorkflowEventType type,
        UUID actorId,
        UUID taskId,
        UUID assigneeId,
        UUID documentId,
        LocalDate anchorDate,
        int depth
) {
    public String idempotencyKey() {
        return switch (type) {
            case TASK_ASSIGNED -> "TASK_ASSIGNED:" + taskId + ":" + assigneeId;
            case TASK_OVERDUE -> "TASK_OVERDUE:" + taskId + ":" + anchorDate;
            case DOCUMENT_UPLOADED -> "DOCUMENT_UPLOADED:" + documentId;
            case DOCUMENT_PROCESSED -> "DOCUMENT_PROCESSED:" + documentId;
            case SCHEDULED_ACTIVITY -> "SCHEDULED_ACTIVITY:" + organizationId + ":" + anchorDate;
        };
    }
}
