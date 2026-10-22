package com.workpilot.ai.repository;

import com.workpilot.ai.domain.WorkflowExecution;
import com.workpilot.ai.domain.WorkflowExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {

    boolean existsByRuleIdAndIdempotencyKey(UUID ruleId, String idempotencyKey);

    @Query("""
            SELECT execution FROM WorkflowExecution execution
            JOIN FETCH execution.rule
            WHERE execution.organizationId = :organizationId
            ORDER BY execution.createdAt DESC
            """)
    List<WorkflowExecution> findRecentByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    @Query("""
            SELECT execution FROM WorkflowExecution execution
            JOIN FETCH execution.rule
            WHERE execution.status = :status
              AND execution.nextAttemptAt IS NOT NULL
              AND execution.nextAttemptAt <= :now
            """)
    List<WorkflowExecution> findDueRetries(
            @Param("status") WorkflowExecutionStatus status,
            @Param("now") Instant now,
            Pageable pageable);
}
