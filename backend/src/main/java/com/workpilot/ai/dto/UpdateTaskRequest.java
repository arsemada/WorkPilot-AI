package com.workpilot.ai.dto;

import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update. A JSON field that is present is applied, including null for
 * description, due date, and assignee. Omitted fields stay unchanged.
 */
public class UpdateTaskRequest {

    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
    private UUID assigneeId;

    private boolean titlePresent;
    private boolean descriptionPresent;
    private boolean statusPresent;
    private boolean priorityPresent;
    private boolean dueDatePresent;
    private boolean assigneePresent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.statusPresent = true;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
        this.priorityPresent = true;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.dueDatePresent = true;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.assigneePresent = true;
    }

    public boolean isTitlePresent() {
        return titlePresent;
    }

    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }

    public boolean isStatusPresent() {
        return statusPresent;
    }

    public boolean isPriorityPresent() {
        return priorityPresent;
    }

    public boolean isDueDatePresent() {
        return dueDatePresent;
    }

    public boolean isAssigneePresent() {
        return assigneePresent;
    }
}
