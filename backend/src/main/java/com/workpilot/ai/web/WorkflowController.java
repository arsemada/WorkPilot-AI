package com.workpilot.ai.web;

import com.workpilot.ai.dto.CreateWorkflowRuleRequest;
import com.workpilot.ai.dto.UpdateWorkflowRuleRequest;
import com.workpilot.ai.dto.WorkflowExecutionResponse;
import com.workpilot.ai.dto.WorkflowRuleResponse;
import com.workpilot.ai.service.WorkflowRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/workflows")
@Tag(name = "Workflows")
public class WorkflowController {

    private final WorkflowRuleService workflowRuleService;

    public WorkflowController(WorkflowRuleService workflowRuleService) {
        this.workflowRuleService = workflowRuleService;
    }

    @GetMapping
    @Operation(summary = "List workflow rules for an organization")
    public List<WorkflowRuleResponse> list(@PathVariable UUID organizationId) {
        return workflowRuleService.list(organizationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a supported workflow rule")
    public WorkflowRuleResponse create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateWorkflowRuleRequest request) {
        return workflowRuleService.create(organizationId, request);
    }

    @PatchMapping("/{ruleId}")
    @Operation(summary = "Rename a workflow rule")
    public WorkflowRuleResponse update(
            @PathVariable UUID organizationId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateWorkflowRuleRequest request) {
        return workflowRuleService.update(organizationId, ruleId, request);
    }

    @PostMapping("/{ruleId}/enable")
    @Operation(summary = "Enable a workflow rule")
    public WorkflowRuleResponse enable(@PathVariable UUID organizationId, @PathVariable UUID ruleId) {
        return workflowRuleService.setEnabled(organizationId, ruleId, true);
    }

    @PostMapping("/{ruleId}/disable")
    @Operation(summary = "Disable a workflow rule")
    public WorkflowRuleResponse disable(@PathVariable UUID organizationId, @PathVariable UUID ruleId) {
        return workflowRuleService.setEnabled(organizationId, ruleId, false);
    }

    @GetMapping("/executions")
    @Operation(summary = "List recent workflow executions")
    public List<WorkflowExecutionResponse> executions(@PathVariable UUID organizationId) {
        return workflowRuleService.executions(organizationId);
    }
}
