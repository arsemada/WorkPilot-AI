package com.workpilot.ai.service;

import com.workpilot.ai.domain.Organization;
import com.workpilot.ai.domain.WorkflowActionType;
import com.workpilot.ai.domain.WorkflowCondition;
import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.domain.WorkflowRule;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.repository.OrganizationRepository;
import com.workpilot.ai.repository.WorkflowRuleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WorkflowCatalog implements ApplicationRunner {

    public record Definition(
            String name,
            WorkflowEventType eventType,
            WorkflowCondition conditionType,
            WorkflowActionType actionType,
            boolean enabled
    ) {
    }

    public static final List<Definition> DEFAULTS = List.of(
            new Definition("Notify the assignee", WorkflowEventType.TASK_ASSIGNED, WorkflowCondition.HAS_ASSIGNEE, WorkflowActionType.NOTIFY_ASSIGNEE, true),
            new Definition("Notify overdue tasks", WorkflowEventType.TASK_OVERDUE, WorkflowCondition.ALWAYS, WorkflowActionType.NOTIFY_RELEVANT_USERS, true),
            new Definition("Process uploaded documents", WorkflowEventType.DOCUMENT_UPLOADED, WorkflowCondition.ALWAYS, WorkflowActionType.PROCESS_DOCUMENT, true),
            new Definition("Summarize processed documents", WorkflowEventType.DOCUMENT_PROCESSED, WorkflowCondition.TEXT_EXTRACTED, WorkflowActionType.SUMMARIZE_DOCUMENT, false),
            new Definition("Daily task activity", WorkflowEventType.SCHEDULED_ACTIVITY, WorkflowCondition.ALWAYS, WorkflowActionType.SUMMARIZE_ACTIVITY, true)
    );

    private final WorkflowRuleRepository ruleRepository;
    private final OrganizationRepository organizationRepository;

    public WorkflowCatalog(WorkflowRuleRepository ruleRepository, OrganizationRepository organizationRepository) {
        this.ruleRepository = ruleRepository;
        this.organizationRepository = organizationRepository;
    }

    public static WorkflowCondition conditionFor(WorkflowEventType eventType, WorkflowActionType actionType) {
        return DEFAULTS.stream()
                .filter(definition -> definition.eventType() == eventType && definition.actionType() == actionType)
                .map(Definition::conditionType)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("That trigger and action are not supported together."));
    }

    @Transactional
    public void ensure(Organization organization) {
        Instant now = Instant.now();
        for (Definition definition : DEFAULTS) {
            if (ruleRepository.existsByOrganizationIdAndEventType(organization.getId(), definition.eventType())) {
                continue;
            }
            WorkflowRule rule = new WorkflowRule();
            rule.setOrganizationId(organization.getId());
            rule.setName(definition.name());
            rule.setEventType(definition.eventType());
            rule.setConditionType(definition.conditionType());
            rule.setActionType(definition.actionType());
            rule.setEnabled(definition.enabled());
            rule.setCreatedAt(now);
            rule.setUpdatedAt(now);
            ruleRepository.save(rule);
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Organization organization : organizationRepository.findAll()) {
            ensure(organization);
        }
    }
}
