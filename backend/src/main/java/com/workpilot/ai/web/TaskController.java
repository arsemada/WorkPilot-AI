package com.workpilot.ai.web;

import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.dto.CreateTaskRequest;
import com.workpilot.ai.dto.TaskBoardResponse;
import com.workpilot.ai.dto.TaskPageResponse;
import com.workpilot.ai.dto.TaskResponse;
import com.workpilot.ai.dto.UpdateTaskRequest;
import com.workpilot.ai.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task in an organization")
    public TaskResponse create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(organizationId, request);
    }

    @GetMapping
    @Operation(summary = "List tasks in an organization")
    public TaskPageResponse list(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) LocalDate dueAfter,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return taskService.list(
                organizationId, status, priority, assigneeId, unassigned,
                dueAfter, dueBefore, q, page, size, sort, direction);
    }

    @GetMapping("/board")
    @Operation(summary = "List tasks grouped by status for a board")
    public TaskBoardResponse board(@PathVariable UUID organizationId) {
        return taskService.board(organizationId);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get one task in an organization")
    public TaskResponse get(@PathVariable UUID organizationId, @PathVariable UUID taskId) {
        return taskService.get(organizationId, taskId);
    }

    @PatchMapping("/{taskId}")
    @Operation(summary = "Update a task in an organization")
    public TaskResponse update(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @RequestBody UpdateTaskRequest request) {
        return taskService.update(organizationId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task in an organization")
    public void delete(@PathVariable UUID organizationId, @PathVariable UUID taskId) {
        taskService.delete(organizationId, taskId);
    }
}
