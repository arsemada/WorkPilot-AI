package com.workpilot.ai.service;

import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.dto.CreateTaskRequest;
import com.workpilot.ai.dto.TaskPageResponse;
import com.workpilot.ai.dto.TaskResponse;
import com.workpilot.ai.dto.UpdateTaskRequest;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.repository.TaskSpecifications;
import com.workpilot.ai.security.OrganizationAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 200;
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "dueDate", "title", "priority", "status");
    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "dueDate", "dueDate",
            "title", "title",
            "priority", "priority",
            "status", "status"
    );

    private final TaskRepository taskRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationAccessService accessService;
    private final ActivityRecorder activityRecorder;

    public TaskService(
            TaskRepository taskRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationAccessService accessService,
            ActivityRecorder activityRecorder) {
        this.taskRepository = taskRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
        this.activityRecorder = activityRecorder;
    }

    @Transactional
    public TaskResponse create(UUID organizationId, CreateTaskRequest request) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_CREATE);
        Task task = new Task();
        task.setOrganization(membership.getOrganization());
        task.setCreatedBy(membership.getUser());
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
        if (request.assigneeId() != null) {
            task.setAssignee(requireEligibleAssignee(organizationId, request.assigneeId()));
        }
        Task saved = taskRepository.save(task);
        activityRecorder.record(
                membership.getOrganization(),
                membership.getUser(),
                saved.getId(),
                ActivityType.TASK_CREATED,
                "created \"" + saved.getTitle() + "\"");
        if (saved.getStatus() == TaskStatus.DONE) {
            activityRecorder.record(
                    membership.getOrganization(),
                    membership.getUser(),
                    saved.getId(),
                    ActivityType.TASK_COMPLETED,
                    "completed \"" + saved.getTitle() + "\"");
        }
        return TaskResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public TaskPageResponse list(
            UUID organizationId,
            TaskStatus status,
            TaskPriority priority,
            UUID assigneeId,
            Boolean unassigned,
            LocalDate dueAfter,
            LocalDate dueBefore,
            String query,
            int page,
            int size,
            String sort,
            String direction) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        if (page < 0) {
            throw new BadRequestException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        if (Boolean.TRUE.equals(unassigned) && assigneeId != null) {
            throw new BadRequestException("Choose either an assignee or unassigned tasks");
        }
        if (dueAfter != null && dueBefore != null && dueAfter.isAfter(dueBefore)) {
            throw new BadRequestException("The due date range is invalid");
        }
        String search = query == null ? null : query.trim();
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be at most 200 characters");
        }

        Specification<Task> specification = TaskSpecifications.inOrganization(organizationId);
        if (status != null) {
            specification = specification.and(TaskSpecifications.hasStatus(status));
        }
        if (priority != null) {
            specification = specification.and(TaskSpecifications.hasPriority(priority));
        }
        if (assigneeId != null) {
            specification = specification.and(TaskSpecifications.hasAssignee(assigneeId));
        }
        if (Boolean.TRUE.equals(unassigned)) {
            specification = specification.and(TaskSpecifications.unassigned());
        }
        if (dueAfter != null) {
            specification = specification.and(TaskSpecifications.dueOnOrAfter(dueAfter));
        }
        if (dueBefore != null) {
            specification = specification.and(TaskSpecifications.dueOnOrBefore(dueBefore));
        }
        if (search != null && !search.isEmpty()) {
            specification = specification.and(TaskSpecifications.textSearch(search));
        }

        Page<Task> result = taskRepository.findAll(
                specification,
                PageRequest.of(page, size, sortBy(sort, direction)));
        List<TaskResponse> items = result.getContent().stream().map(TaskResponse::fromEntity).toList();
        return new TaskPageResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID organizationId, UUID taskId) {
        return TaskResponse.fromEntity(requireTask(organizationId, taskId, Permission.RESOURCE_READ).task());
    }

    private record AuthorizedTask(OrganizationMembership membership, Task task) {
    }

    @Transactional
    public TaskResponse update(UUID organizationId, UUID taskId, UpdateTaskRequest request) {
        AuthorizedTask authorized = requireTask(organizationId, taskId, Permission.RESOURCE_UPDATE);
        Task task = authorized.task();
        TaskStatus previousStatus = task.getStatus();
        boolean statusChanged = false;
        boolean fieldsChanged = false;
        if (request.isTitlePresent()) {
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new BadRequestException("Title is required");
            }
            String title = request.getTitle().trim();
            if (title.length() > 200) {
                throw new BadRequestException("Title must be at most 200 characters");
            }
            if (!title.equals(task.getTitle())) {
                fieldsChanged = true;
            }
            task.setTitle(title);
        }
        if (request.isDescriptionPresent()) {
            String description = blankToNull(request.getDescription());
            if (description != null && description.length() > 5000) {
                throw new BadRequestException("Description must be at most 5000 characters");
            }
            if (!Objects.equals(description, task.getDescription())) {
                fieldsChanged = true;
            }
            task.setDescription(description);
        }
        if (request.isStatusPresent()) {
            if (request.getStatus() == null) {
                throw new BadRequestException("Status is required");
            }
            if (!task.getStatus().canTransitionTo(request.getStatus())) {
                throw new BadRequestException(
                        "Cannot change status from " + task.getStatus() + " to " + request.getStatus());
            }
            statusChanged = request.getStatus() != previousStatus;
            task.setStatus(request.getStatus());
        }
        if (request.isPriorityPresent()) {
            if (request.getPriority() == null) {
                throw new BadRequestException("Priority is required");
            }
            if (request.getPriority() != task.getPriority()) {
                fieldsChanged = true;
            }
            task.setPriority(request.getPriority());
        }
        if (request.isDueDatePresent()) {
            if (!Objects.equals(request.getDueDate(), task.getDueDate())) {
                fieldsChanged = true;
            }
            task.setDueDate(request.getDueDate());
        }
        if (request.isAssigneePresent()) {
            UUID previousAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
            if (!Objects.equals(request.getAssigneeId(), previousAssigneeId)) {
                fieldsChanged = true;
            }
            if (request.getAssigneeId() == null) {
                task.setAssignee(null);
            } else {
                task.setAssignee(requireEligibleAssignee(organizationId, request.getAssigneeId()));
            }
        }
        Task saved = taskRepository.save(task);
        if (statusChanged && saved.getStatus() == TaskStatus.DONE) {
            activityRecorder.record(
                    authorized.membership().getOrganization(),
                    authorized.membership().getUser(),
                    saved.getId(),
                    ActivityType.TASK_COMPLETED,
                    "completed \"" + saved.getTitle() + "\"");
        } else if (statusChanged) {
            activityRecorder.record(
                    authorized.membership().getOrganization(),
                    authorized.membership().getUser(),
                    saved.getId(),
                    ActivityType.TASK_STATUS_CHANGED,
                    "changed \"" + saved.getTitle() + "\" from " + previousStatus + " to " + saved.getStatus());
        } else if (fieldsChanged) {
            activityRecorder.record(
                    authorized.membership().getOrganization(),
                    authorized.membership().getUser(),
                    saved.getId(),
                    ActivityType.TASK_UPDATED,
                    "updated \"" + saved.getTitle() + "\"");
        }
        return TaskResponse.fromEntity(saved);
    }

    @Transactional
    public void delete(UUID organizationId, UUID taskId) {
        AuthorizedTask authorized = requireTask(organizationId, taskId, Permission.RESOURCE_DELETE);
        Task task = authorized.task();
        String title = task.getTitle();
        UUID id = task.getId();
        taskRepository.delete(task);
        activityRecorder.record(
                authorized.membership().getOrganization(),
                authorized.membership().getUser(),
                id,
                ActivityType.TASK_DELETED,
                "deleted \"" + title + "\"");
    }

    private AuthorizedTask requireTask(UUID organizationId, UUID taskId, Permission permission) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, permission);
        Task task = taskRepository.findByIdAndOrganizationId(taskId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        return new AuthorizedTask(membership, task);
    }

    private User requireEligibleAssignee(UUID organizationId, UUID assigneeId) {
        OrganizationMembership membership = membershipRepository
                .findWithDetailsByOrganizationIdAndUserId(organizationId, assigneeId)
                .orElseThrow(() -> new BadRequestException("Assignee must be a member of this organization"));
        if (!membership.getUser().isActive()) {
            throw new BadRequestException("Assignee must be an active member of this organization");
        }
        return membership.getUser();
    }

    private Sort sortBy(String sort, String direction) {
        String property = SORT_PROPERTIES.get(sort);
        if (property == null || !SORT_FIELDS.contains(sort)) {
            throw new BadRequestException("Unsupported sort field");
        }
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new BadRequestException("Sort direction must be asc or desc");
        }
        Sort.Order order = "asc".equalsIgnoreCase(direction)
                ? Sort.Order.asc(property)
                : Sort.Order.desc(property);
        return Sort.by(order);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
