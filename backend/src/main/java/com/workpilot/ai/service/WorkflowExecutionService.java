package com.workpilot.ai.service;

import com.workpilot.ai.config.WorkflowProperties;
import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.domain.Organization;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.OrganizationRole;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.domain.WorkflowExecution;
import com.workpilot.ai.domain.WorkflowExecutionStatus;
import com.workpilot.ai.domain.WorkflowRule;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import com.workpilot.ai.repository.OrganizationRepository;
import com.workpilot.ai.repository.UserRepository;
import com.workpilot.ai.repository.WorkflowExecutionRepository;
import com.workpilot.ai.repository.WorkflowRuleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowExecutionService {

    static final int MAX_ATTEMPTS = 3;

    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowRuleRepository ruleRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ActivityRecorder activityRecorder;
    private final WorkflowProperties properties;

    public WorkflowExecutionService(
            WorkflowExecutionRepository executionRepository,
            WorkflowRuleRepository ruleRepository,
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository,
            ActivityRecorder activityRecorder,
            WorkflowProperties properties) {
        this.executionRepository = executionRepository;
        this.ruleRepository = ruleRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.activityRecorder = activityRecorder;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> claim(UUID ruleId, WorkflowTrigger trigger) {
        WorkflowRule rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null || !rule.isEnabled() || !rule.getOrganizationId().equals(trigger.organizationId())) {
            return Optional.empty();
        }
        String key = trigger.idempotencyKey();
        if (key.length() > 200 || executionRepository.existsByRuleIdAndIdempotencyKey(rule.getId(), key)) {
            return Optional.empty();
        }
        WorkflowExecution execution = newExecution(rule, trigger, WorkflowExecutionStatus.RUNNING, null);
        execution.setAttemptCount(1);
        try {
            return Optional.of(executionRepository.saveAndFlush(execution).getId());
        } catch (DataIntegrityViolationException exception) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skip(UUID ruleId, WorkflowTrigger trigger, String message) {
        WorkflowRule rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null || !rule.getOrganizationId().equals(trigger.organizationId())) {
            return;
        }
        String key = trigger.idempotencyKey();
        if (executionRepository.existsByRuleIdAndIdempotencyKey(rule.getId(), key)) {
            return;
        }
        WorkflowExecution execution = newExecution(rule, trigger, WorkflowExecutionStatus.SKIPPED, limit(message));
        execution.setResultMessage(limit(message));
        execution.setErrorMessage(null);
        execution.setFinishedAt(Instant.now());
        try {
            executionRepository.saveAndFlush(execution);
        } catch (DataIntegrityViolationException exception) {
            // The same event was already recorded.
        }
    }

    @Transactional
    public void succeed(UUID executionId, String message) {
        WorkflowExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null || execution.getStatus() == WorkflowExecutionStatus.FAILED
                || execution.getStatus() == WorkflowExecutionStatus.SKIPPED
                || execution.getStatus() == WorkflowExecutionStatus.SUCCEEDED) {
            return;
        }
        execution.setStatus(WorkflowExecutionStatus.SUCCEEDED);
        execution.setResultMessage(limit(message));
        execution.setErrorMessage(null);
        execution.setFinishedAt(Instant.now());
        execution.setNextAttemptAt(null);
        audit(execution, true, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID executionId, String message) {
        WorkflowExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null || execution.getStatus() == WorkflowExecutionStatus.SUCCEEDED
                || execution.getStatus() == WorkflowExecutionStatus.SKIPPED) {
            return;
        }
        markFailed(execution, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishDocument(UUID executionId, DocumentRunResult result) {
        if (executionId == null || result == null) {
            return;
        }
        WorkflowExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null || execution.getStatus() != WorkflowExecutionStatus.RUNNING) {
            return;
        }
        if (result.kind() == DocumentRunResult.Kind.FAILED) {
            markFailed(execution, result.message());
            return;
        }
        execution.setStatus(result.kind() == DocumentRunResult.Kind.SKIPPED
                ? WorkflowExecutionStatus.SKIPPED
                : WorkflowExecutionStatus.SUCCEEDED);
        execution.setResultMessage(limit(result.message()));
        execution.setErrorMessage(null);
        execution.setFinishedAt(Instant.now());
        execution.setNextAttemptAt(null);
        if (result.kind() == DocumentRunResult.Kind.SUCCEEDED) {
            audit(execution, true, result.message());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<RetryWork> beginRetry(UUID executionId) {
        WorkflowExecution execution = executionRepository.findById(executionId).orElse(null);
        if (execution == null || execution.getStatus() != WorkflowExecutionStatus.FAILED || execution.getNextAttemptAt() == null) {
            return Optional.empty();
        }
        if (execution.getNextAttemptAt().isAfter(Instant.now()) || execution.getAttemptCount() >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        execution.setAttemptCount(execution.getAttemptCount() + 1);
        execution.setStatus(WorkflowExecutionStatus.RUNNING);
        execution.setNextAttemptAt(null);
        execution.setErrorMessage(null);
        executionRepository.saveAndFlush(execution);
        WorkflowTrigger trigger = new WorkflowTrigger(
                execution.getOrganizationId(),
                execution.getEventType(),
                execution.getActorId(),
                execution.getTaskId(),
                execution.getAssigneeId(),
                execution.getDocumentId(),
                execution.getAnchorDate(),
                execution.getDepth());
        return Optional.of(new RetryWork(execution.getId(), execution.getRule().getActionType(), trigger));
    }

    public java.util.List<UUID> dueIds() {
        return executionRepository.findDueRetries(WorkflowExecutionStatus.FAILED, Instant.now(), PageRequest.of(0, 20))
                .stream()
                .map(WorkflowExecution::getId)
                .toList();
    }

    private void markFailed(WorkflowExecution execution, String message) {
        execution.setStatus(WorkflowExecutionStatus.FAILED);
        execution.setErrorMessage(limit(message));
        execution.setResultMessage(null);
        execution.setFinishedAt(Instant.now());
        if (execution.getAttemptCount() < MAX_ATTEMPTS) {
            var backoff = properties.getRetryBackoff() == null ? java.time.Duration.ofMinutes(1) : properties.getRetryBackoff();
            execution.setNextAttemptAt(Instant.now().plus(backoff));
        } else {
            execution.setNextAttemptAt(null);
        }
        audit(execution, false, execution.getErrorMessage());
    }

    private WorkflowExecution newExecution(WorkflowRule rule, WorkflowTrigger trigger, WorkflowExecutionStatus status, String error) {
        WorkflowExecution execution = new WorkflowExecution();
        execution.setRule(rule);
        execution.setOrganizationId(rule.getOrganizationId());
        execution.setEventType(trigger.type());
        execution.setIdempotencyKey(trigger.idempotencyKey());
        execution.setStatus(status);
        execution.setAttemptCount(1);
        execution.setErrorMessage(error);
        execution.setTaskId(trigger.taskId());
        execution.setAssigneeId(trigger.assigneeId());
        execution.setDocumentId(trigger.documentId());
        execution.setActorId(trigger.actorId());
        execution.setAnchorDate(trigger.anchorDate());
        execution.setDepth(trigger.depth());
        execution.setCreatedAt(Instant.now());
        return execution;
    }

    private void audit(WorkflowExecution execution, boolean succeeded, String detail) {
        Organization organization = organizationRepository.findById(execution.getOrganizationId()).orElse(null);
        User actor = actor(execution);
        if (organization == null || actor == null) {
            return;
        }
        String name = execution.getRule().getName();
        String summary = succeeded
                ? "ran workflow \"" + name + "\""
                : "workflow \"" + name + "\" failed: " + (detail == null ? "The workflow action failed." : detail);
        activityRecorder.record(
                organization,
                actor,
                execution.getTaskId(),
                succeeded ? ActivityType.WORKFLOW_SUCCEEDED : ActivityType.WORKFLOW_FAILED,
                summary);
    }

    private User actor(WorkflowExecution execution) {
        if (execution.getActorId() != null) {
            Optional<User> actor = userRepository.findById(execution.getActorId());
            if (actor.isPresent()) {
                return actor.get();
            }
        }
        return membershipRepository.findAllWithUserByOrganizationId(execution.getOrganizationId()).stream()
                .filter(membership -> membership.getRole() == OrganizationRole.OWNER)
                .map(OrganizationMembership::getUser)
                .findFirst()
                .orElse(null);
    }

    private static String limit(String value) {
        if (value == null || value.isBlank()) {
            return "The workflow action failed.";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    public record RetryWork(UUID executionId, com.workpilot.ai.domain.WorkflowActionType actionType, WorkflowTrigger trigger) {
    }
}
