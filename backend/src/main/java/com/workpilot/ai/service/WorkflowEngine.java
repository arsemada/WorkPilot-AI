package com.workpilot.ai.service;

import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.WorkflowActionType;
import com.workpilot.ai.domain.WorkflowCondition;
import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.domain.WorkflowRule;
import com.workpilot.ai.exception.AiUnavailableException;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.DocumentRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.repository.TaskSpecifications;
import com.workpilot.ai.repository.WorkflowRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final String FAILED = "The workflow action failed.";

    private final WorkflowRuleRepository ruleRepository;
    private final WorkflowExecutionService executionService;
    private final WorkflowActions actions;
    private final TaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowEngine(
            WorkflowRuleRepository ruleRepository,
            WorkflowExecutionService executionService,
            WorkflowActions actions,
            TaskRepository taskRepository,
            DocumentRepository documentRepository,
            ApplicationEventPublisher eventPublisher) {
        this.ruleRepository = ruleRepository;
        this.executionService = executionService;
        this.actions = actions;
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(WorkflowTrigger trigger) {
        if (trigger == null || trigger.organizationId() == null || trigger.type() == null || trigger.depth() > 1) {
            return;
        }
        List<WorkflowRule> rules = ruleRepository.findByOrganizationIdAndEventTypeAndEnabledTrue(
                trigger.organizationId(), trigger.type());
        for (WorkflowRule rule : rules) {
            runRule(rule, trigger);
        }
    }

    @Transactional(readOnly = true)
    public void scanOverdue(LocalDate today) {
        List<WorkflowTrigger> triggers = new ArrayList<>();
        for (Task task : taskRepository.findAll(TaskSpecifications.overdue(today))) {
            triggers.add(new WorkflowTrigger(
                    task.getOrganization().getId(),
                    WorkflowEventType.TASK_OVERDUE,
                    null,
                    task.getId(),
                    task.getAssignee() == null ? null : task.getAssignee().getId(),
                    null,
                    task.getDueDate(),
                    0));
        }
        for (WorkflowTrigger trigger : triggers) {
            eventPublisher.publishEvent(trigger);
        }
    }

    public void summarizeActivity(LocalDate day) {
        for (WorkflowRule rule : ruleRepository.findByEventTypeAndEnabledTrue(WorkflowEventType.SCHEDULED_ACTIVITY)) {
            handle(new WorkflowTrigger(
                    rule.getOrganizationId(),
                    WorkflowEventType.SCHEDULED_ACTIVITY,
                    null,
                    null,
                    null,
                    null,
                    day,
                    0));
        }
    }

    public void retryDue() {
        for (var executionId : executionService.dueIds()) {
            executionService.beginRetry(executionId).ifPresent(this::retry);
        }
    }

    private void runRule(WorkflowRule rule, WorkflowTrigger trigger) {
        if (!conditionMatches(rule, trigger)) {
            executionService.skip(rule.getId(), trigger, skipMessage(rule.getConditionType()));
            return;
        }
        executionService.claim(rule.getId(), trigger).ifPresent(executionId -> execute(executionId, rule, trigger));
    }

    private void retry(WorkflowExecutionService.RetryWork work) {
        WorkflowRule rule = ruleRepository.findByOrganizationIdAndEventTypeAndEnabledTrue(
                        work.trigger().organizationId(), work.trigger().type())
                .stream()
                .filter(candidate -> candidate.getActionType() == work.actionType())
                .findFirst()
                .orElse(null);
        if (rule == null || !rule.isEnabled()) {
            executionService.fail(work.executionId(), "The workflow is disabled.");
            return;
        }
        execute(work.executionId(), rule, work.trigger());
    }

    private void execute(java.util.UUID executionId, WorkflowRule rule, WorkflowTrigger trigger) {
        try {
            if (rule.getActionType() == WorkflowActionType.PROCESS_DOCUMENT) {
                actions.processDocument(executionId, trigger);
                return;
            }
            actions.runInline(executionId, rule.getId(), trigger);
        } catch (RuntimeException exception) {
            log.warn("Workflow action failed: {}", exception.getClass().getSimpleName());
            executionService.fail(executionId, safe(exception));
        }
    }

    private boolean conditionMatches(WorkflowRule rule, WorkflowTrigger trigger) {
        return switch (rule.getConditionType()) {
            case ALWAYS -> true;
            case HAS_ASSIGNEE -> trigger.assigneeId() != null;
            case TEXT_EXTRACTED -> hasText(trigger);
        };
    }

    private boolean hasText(WorkflowTrigger trigger) {
        if (trigger.documentId() == null) {
            return false;
        }
        Document document = documentRepository.findByIdAndOrganizationId(trigger.documentId(), trigger.organizationId()).orElse(null);
        return document != null && document.getExtractedText() != null && !document.getExtractedText().isBlank();
    }

    private static String skipMessage(WorkflowCondition condition) {
        return switch (condition) {
            case HAS_ASSIGNEE -> "The task has no assignee.";
            case TEXT_EXTRACTED -> "The document has no extracted text.";
            case ALWAYS -> "The workflow condition was not met.";
        };
    }

    private static String safe(RuntimeException exception) {
        if (exception instanceof AiUnavailableException
                || exception instanceof BadRequestException
                || exception instanceof ResourceNotFoundException) {
            String message = exception.getMessage();
            if (message != null && !message.isBlank()) {
                return message.length() <= 300 ? message.trim() : message.trim().substring(0, 300);
            }
        }
        return FAILED;
    }
}
