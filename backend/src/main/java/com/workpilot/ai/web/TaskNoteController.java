package com.workpilot.ai.web;

import com.workpilot.ai.dto.CreateCommentRequest;
import com.workpilot.ai.dto.CreateTaskDocumentLinkRequest;
import com.workpilot.ai.dto.TaskCommentResponse;
import com.workpilot.ai.dto.TaskDocumentLinkResponse;
import com.workpilot.ai.service.TaskNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/tasks/{taskId}")
@Tag(name = "Task notes")
public class TaskNoteController {

    private final TaskNoteService taskNoteService;

    public TaskNoteController(TaskNoteService taskNoteService) {
        this.taskNoteService = taskNoteService;
    }

    @GetMapping("/comments")
    @Operation(summary = "List comments on a task")
    public List<TaskCommentResponse> listComments(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId) {
        return taskNoteService.listComments(organizationId, taskId);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment to a task")
    public TaskCommentResponse addComment(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        return taskNoteService.addComment(organizationId, taskId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a comment on a task")
    public void deleteComment(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @PathVariable UUID commentId) {
        taskNoteService.deleteComment(organizationId, taskId, commentId);
    }

    @GetMapping("/documents")
    @Operation(summary = "List documents linked to a task")
    public List<TaskDocumentLinkResponse> listDocuments(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId) {
        return taskNoteService.listDocuments(organizationId, taskId);
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Link an organization document to a task")
    public TaskDocumentLinkResponse linkDocument(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskDocumentLinkRequest request) {
        return taskNoteService.linkDocument(organizationId, taskId, request);
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a document link from a task")
    public void unlinkDocument(
            @PathVariable UUID organizationId,
            @PathVariable UUID taskId,
            @PathVariable UUID documentId) {
        taskNoteService.unlinkDocument(organizationId, taskId, documentId);
    }
}
