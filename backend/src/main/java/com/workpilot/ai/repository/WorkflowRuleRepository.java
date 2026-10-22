package com.workpilot.ai.repository;

import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.domain.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, UUID> {

    List<WorkflowRule> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    List<WorkflowRule> findByOrganizationIdAndEventTypeAndEnabledTrue(UUID organizationId, WorkflowEventType eventType);

    List<WorkflowRule> findByEventTypeAndEnabledTrue(WorkflowEventType eventType);

    Optional<WorkflowRule> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndEventType(UUID organizationId, WorkflowEventType eventType);
}
