package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.domain.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        UUID createdById,
        String createdByName,
        UUID assigneeId,
        String assigneeName,
        Instant createdAt,
        Instant updatedAt,
        List<TaskStatus> permittedStatuses
) {
    public static TaskResponse fromEntity(Task task) {
        User creator = task.getCreatedBy();
        User assignee = task.getAssignee();
        List<TaskStatus> permitted = new ArrayList<>();
        permitted.add(task.getStatus());
        permitted.addAll(task.getStatus().allowedTargets());
        return new TaskResponse(
                task.getId(),
                task.scopedOrganizationId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                creator.getId(),
                creator.getFullName(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getFullName(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                permitted
        );
    }
}
