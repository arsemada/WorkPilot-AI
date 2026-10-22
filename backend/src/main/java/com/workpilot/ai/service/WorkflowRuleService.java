package com.workpilot.ai.service;

import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.WorkflowRule;
import com.workpilot.ai.dto.CreateWorkflowRuleRequest;
import com.workpilot.ai.dto.UpdateWorkflowRuleRequest;
import com.workpilot.ai.dto.WorkflowExecutionResponse;
import com.workpilot.ai.dto.WorkflowRuleResponse;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.WorkflowExecutionRepository;
import com.workpilot.ai.repository.WorkflowRuleRepository;
import com.workpilot.ai.security.OrganizationAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowRuleService {

    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final OrganizationAccessService accessService;

    public WorkflowRuleService(
            WorkflowRuleRepository ruleRepository,
            WorkflowExecutionRepository executionRepository,
            OrganizationAccessService accessService) {
        this.ruleRepository = ruleRepository;
        this.executionRepository = executionRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<WorkflowRuleResponse> list(UUID organizationId) {
        accessService.requirePermission(organizationId, Permission.ORG_READ);
        return ruleRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .map(WorkflowRuleResponse::fromEntity)
                .toList();
    }

    @Transactional
    public WorkflowRuleResponse create(UUID organizationId, CreateWorkflowRuleRequest request) {
        accessService.requirePermission(organizationId, Permission.ORG_UPDATE);
        WorkflowCatalog.conditionFor(request.eventType(), request.actionType());
        if (ruleRepository.existsByOrganizationIdAndEventType(organizationId, request.eventType())) {
            throw new BadRequestException("This organization already has a workflow for that event.");
        }
        Instant now = Instant.now();
        WorkflowRule rule = new WorkflowRule();
        rule.setOrganizationId(organizationId);
        rule.setName(request.name().trim());
        rule.setEventType(request.eventType());
        rule.setActionType(request.actionType());
        rule.setConditionType(WorkflowCatalog.conditionFor(request.eventType(), request.actionType()));
        rule.setEnabled(true);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        try {
            return WorkflowRuleResponse.fromEntity(ruleRepository.saveAndFlush(rule));
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("This organization already has a workflow for that event.");
        }
    }

    @Transactional
    public WorkflowRuleResponse update(UUID organizationId, UUID ruleId, UpdateWorkflowRuleRequest request) {
        WorkflowRule rule = requireRule(organizationId, ruleId, Permission.ORG_UPDATE);
        rule.setName(request.name().trim());
        rule.setUpdatedAt(Instant.now());
        return WorkflowRuleResponse.fromEntity(rule);
    }

    @Transactional
    public WorkflowRuleResponse setEnabled(UUID organizationId, UUID ruleId, boolean enabled) {
        WorkflowRule rule = requireRule(organizationId, ruleId, Permission.ORG_UPDATE);
        rule.setEnabled(enabled);
        rule.setUpdatedAt(Instant.now());
        return WorkflowRuleResponse.fromEntity(rule);
    }

    @Transactional(readOnly = true)
    public List<WorkflowExecutionResponse> executions(UUID organizationId) {
        accessService.requirePermission(organizationId, Permission.ORG_READ);
        return executionRepository.findRecentByOrganizationId(organizationId, PageRequest.of(0, 50)).stream()
                .map(WorkflowExecutionResponse::fromEntity)
                .toList();
    }

    private WorkflowRule requireRule(UUID organizationId, UUID ruleId, Permission permission) {
        accessService.requirePermission(organizationId, permission);
        return ruleRepository.findByIdAndOrganizationId(ruleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
    }
}
