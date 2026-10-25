package com.workpilot.ai.repository;

import com.workpilot.ai.domain.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    @Query("""
            SELECT c FROM TaskComment c
            JOIN FETCH c.author
            JOIN FETCH c.task
            WHERE c.task.id = :taskId AND c.organization.id = :organizationId
            ORDER BY c.createdAt ASC
            """)
    List<TaskComment> findByTaskIdAndOrganizationId(
            @Param("taskId") UUID taskId,
            @Param("organizationId") UUID organizationId);

    @Query("""
            SELECT c FROM TaskComment c
            JOIN FETCH c.author
            JOIN FETCH c.task
            WHERE c.id = :id AND c.task.id = :taskId AND c.organization.id = :organizationId
            """)
    Optional<TaskComment> findByIdAndTaskIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("taskId") UUID taskId,
            @Param("organizationId") UUID organizationId);
}
