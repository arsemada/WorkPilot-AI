package com.workpilot.ai.dto;

import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        UUID assigneeId
) {
}
