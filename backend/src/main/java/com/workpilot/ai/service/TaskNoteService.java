package com.workpilot.ai.service;

import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.domain.Document;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.TaskComment;
import com.workpilot.ai.domain.TaskDocumentLink;
import com.workpilot.ai.dto.CreateCommentRequest;
import com.workpilot.ai.dto.CreateTaskDocumentLinkRequest;
import com.workpilot.ai.dto.TaskCommentResponse;
import com.workpilot.ai.dto.TaskDocumentLinkResponse;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ForbiddenException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.DocumentRepository;
import com.workpilot.ai.repository.TaskCommentRepository;
import com.workpilot.ai.repository.TaskDocumentLinkRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.security.OrganizationAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TaskNoteService {

    private static final int MAX_LINKS = 20;

    private final TaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskDocumentLinkRepository linkRepository;
    private final OrganizationAccessService accessService;
    private final ActivityRecorder activityRecorder;

    public TaskNoteService(
            TaskRepository taskRepository,
            DocumentRepository documentRepository,
            TaskCommentRepository commentRepository,
            TaskDocumentLinkRepository linkRepository,
            OrganizationAccessService accessService,
            ActivityRecorder activityRecorder) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.commentRepository = commentRepository;
        this.linkRepository = linkRepository;
        this.accessService = accessService;
        this.activityRecorder = activityRecorder;
    }

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> listComments(UUID organizationId, UUID taskId) {
        Task task = requireTask(organizationId, taskId, Permission.RESOURCE_READ);
        return commentRepository.findByTaskIdAndOrganizationId(task.getId(), organizationId).stream()
                .map(TaskCommentResponse::fromEntity)
                .toList();
    }

    @Transactional
    public TaskCommentResponse addComment(UUID organizationId, UUID taskId, CreateCommentRequest request) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_UPDATE);
        Task task = requireExistingTask(organizationId, taskId);
        TaskComment comment = new TaskComment();
        comment.setOrganization(membership.getOrganization());
        comment.setTask(task);
        comment.setAuthor(membership.getUser());
        comment.setBody(request.body().trim());
        TaskComment saved = commentRepository.save(comment);
        activityRecorder.record(
                membership.getOrganization(),
                membership.getUser(),
                task.getId(),
                ActivityType.TASK_COMMENTED,
                "commented on \"" + task.getTitle() + "\"");
        return TaskCommentResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteComment(UUID organizationId, UUID taskId, UUID commentId) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        TaskComment comment = commentRepository
                .findByIdAndTaskIdAndOrganizationId(commentId, taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        boolean owner = comment.getAuthor().getId().equals(membership.getUser().getId());
        boolean canDeleteAny = membership.getRole().hasPermission(Permission.RESOURCE_DELETE);
        if (!owner && !canDeleteAny) {
            throw new ForbiddenException("You do not have permission to perform this action");
        }
        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<TaskDocumentLinkResponse> listDocuments(UUID organizationId, UUID taskId) {
        Task task = requireTask(organizationId, taskId, Permission.RESOURCE_READ);
        return linkRepository.findByTaskIdAndOrganizationId(task.getId(), organizationId).stream()
                .map(TaskDocumentLinkResponse::fromEntity)
                .toList();
    }

    @Transactional
    public TaskDocumentLinkResponse linkDocument(
            UUID organizationId,
            UUID taskId,
            CreateTaskDocumentLinkRequest request) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_UPDATE);
        Task task = requireExistingTask(organizationId, taskId);
        Document document = documentRepository
                .findByIdAndOrganizationId(request.documentId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (linkRepository.existsByTaskIdAndDocumentId(task.getId(), document.getId())) {
            throw new BadRequestException("This document is already linked to the task.");
        }
        if (linkRepository.findByTaskIdAndOrganizationId(task.getId(), organizationId).size() >= MAX_LINKS) {
            throw new BadRequestException("A task can link at most 20 documents.");
        }
        TaskDocumentLink link = new TaskDocumentLink();
        link.setOrganization(membership.getOrganization());
        link.setTask(task);
        link.setDocument(document);
        link.setAttachedBy(membership.getUser());
        TaskDocumentLink saved = linkRepository.save(link);
        activityRecorder.record(
                membership.getOrganization(),
                membership.getUser(),
                task.getId(),
                ActivityType.TASK_DOCUMENT_LINKED,
                "linked \"" + document.getTitle() + "\" to \"" + task.getTitle() + "\"");
        return TaskDocumentLinkResponse.fromEntity(saved);
    }

    @Transactional
    public void unlinkDocument(UUID organizationId, UUID taskId, UUID documentId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_UPDATE);
        requireExistingTask(organizationId, taskId);
        TaskDocumentLink link = linkRepository
                .findByTaskIdAndDocumentIdAndOrganizationId(taskId, documentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Document link not found"));
        linkRepository.delete(link);
    }

    private Task requireTask(UUID organizationId, UUID taskId, Permission permission) {
        accessService.requirePermission(organizationId, permission);
        return requireExistingTask(organizationId, taskId);
    }

    private Task requireExistingTask(UUID organizationId, UUID taskId) {
        return taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }
}
