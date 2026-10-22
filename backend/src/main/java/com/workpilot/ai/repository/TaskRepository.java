package com.workpilot.ai.repository;

import com.workpilot.ai.domain.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.organization.id = :organizationId")
    Optional<Task> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @Query("SELECT t.status, COUNT(t) FROM Task t WHERE t.organization.id = :organizationId GROUP BY t.status")
    List<Object[]> countGroupedByStatus(@Param("organizationId") UUID organizationId);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.organization.id = :organizationId
              AND t.dueDate < :today
              AND t.status IN (
                  com.workpilot.ai.domain.TaskStatus.TODO,
                  com.workpilot.ai.domain.TaskStatus.IN_PROGRESS
              )
            """)
    long countOverdue(@Param("organizationId") UUID organizationId, @Param("today") LocalDate today);

    @Query("""
            SELECT t FROM Task t
            JOIN FETCH t.createdBy
            LEFT JOIN FETCH t.assignee
            WHERE t.organization.id = :organizationId
            ORDER BY t.updatedAt DESC
            """)
    List<Task> findRecentByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);
}
