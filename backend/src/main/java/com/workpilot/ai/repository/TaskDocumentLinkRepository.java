package com.workpilot.ai.repository;

import com.workpilot.ai.domain.TaskDocumentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskDocumentLinkRepository extends JpaRepository<TaskDocumentLink, UUID> {

    @Query("""
            SELECT l FROM TaskDocumentLink l
            JOIN FETCH l.document
            JOIN FETCH l.attachedBy
            JOIN FETCH l.task
            WHERE l.task.id = :taskId AND l.organization.id = :organizationId
            ORDER BY l.createdAt ASC
            """)
    List<TaskDocumentLink> findByTaskIdAndOrganizationId(
            @Param("taskId") UUID taskId,
            @Param("organizationId") UUID organizationId);

    boolean existsByTaskIdAndDocumentId(UUID taskId, UUID documentId);

    @Query("""
            SELECT l FROM TaskDocumentLink l
            JOIN FETCH l.document
            WHERE l.task.id = :taskId AND l.document.id = :documentId AND l.organization.id = :organizationId
            """)
    Optional<TaskDocumentLink> findByTaskIdAndDocumentIdAndOrganizationId(
            @Param("taskId") UUID taskId,
            @Param("documentId") UUID documentId,
            @Param("organizationId") UUID organizationId);
}
