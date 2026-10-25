package com.workpilot.ai.dto;

import com.workpilot.ai.domain.TaskComment;

import java.time.Instant;
import java.util.UUID;

public record TaskCommentResponse(
        UUID id,
        UUID taskId,
        UUID organizationId,
        String body,
        UUID authorId,
        String authorName,
        Instant createdAt
) {
    public static TaskCommentResponse fromEntity(TaskComment comment) {
        return new TaskCommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                comment.scopedOrganizationId(),
                comment.getBody(),
                comment.getAuthor().getId(),
                comment.getAuthor().getFullName(),
                comment.getCreatedAt());
    }
}
