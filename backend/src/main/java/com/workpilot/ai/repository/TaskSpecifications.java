package com.workpilot.ai.repository;

import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> hasAssignee(UUID assigneeId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignee"));
    }

    public static Specification<Task> dueOnOrAfter(LocalDate dueAfter) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), dueAfter);
    }

    public static Specification<Task> dueOnOrBefore(LocalDate dueBefore) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), dueBefore);
    }

    public static Specification<Task> textSearch(String rawQuery) {
        String escaped = rawQuery.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern, '\\')
        );
    }
}
