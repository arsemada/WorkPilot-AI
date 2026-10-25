package com.workpilot.ai.service;

import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.Task;
import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskRecurrence;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.domain.WorkflowEventType;
import com.workpilot.ai.dto.CreateTaskRequest;
import com.workpilot.ai.dto.MyWorkResponse;
import com.workpilot.ai.dto.TaskBoardResponse;
import com.workpilot.ai.dto.TaskPageResponse;
import com.workpilot.ai.dto.TaskResponse;
import com.workpilot.ai.dto.UpdateTaskRequest;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.repository.TaskSpecifications;
import com.workpilot.ai.security.OrganizationAccessService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEARCH_LENGTH = 200;
    private static final int MY_WORK_FETCH = 200;
    private static final int MY_WORK_SECTION = 20;
    private static final int BOARD_OPEN_LIMIT = 50;
    private static final int BOARD_CLOSED_LIMIT = 20;
    private static final Comparator<Task> OPEN_TASK_ORDER = Comparator
            .comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Task::getUpdatedAt, Comparator.reverseOrder());
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
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(
            TaskRepository taskRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationAccessService accessService,
            ActivityRecorder activityRecorder,
            ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
        this.activityRecorder = activityRecorder;
        this.eventPublisher = eventPublisher;
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
        task.setRecurrence(request.recurrence() == null ? TaskRecurrence.NONE : request.recurrence());
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
            spawnNextOccurrence(saved, membership);
        }
        if (saved.getAssignee() != null) {
            publishAssignment(organizationId, membership.getUser().getId(), saved);
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
    public List<TaskResponse> lookupForAssistant(
            UUID organizationId,
            TaskStatus status,
            TaskPriority priority,
            UUID assigneeId,
            boolean overdue,
            String query) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        String search = query == null ? null : query.trim();
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be at most 200 characters");
        }
        Specification<Task> specification = TaskSpecifications.inOrganization(organizationId);
        if (overdue) {
            specification = specification.and(TaskSpecifications.overdue(LocalDate.now(ZoneOffset.UTC)));
        }
        if (status != null) {
            specification = specification.and(TaskSpecifications.hasStatus(status));
        }
        if (priority != null) {
            specification = specification.and(TaskSpecifications.hasPriority(priority));
        }
        if (assigneeId != null) {
            specification = specification.and(TaskSpecifications.hasAssignee(assigneeId));
        }
        if (search != null && !search.isEmpty()) {
            specification = specification.and(TaskSpecifications.textSearch(search));
        }
        return taskRepository.findAll(specification, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .stream()
                .map(TaskResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public MyWorkResponse myWork(UUID organizationId) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        UUID userId = membership.getUser().getId();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Specification<Task> specification = TaskSpecifications.inOrganization(organizationId)
                .and(TaskSpecifications.hasAssignee(userId))
                .and(TaskSpecifications.hasOpenStatus());
        List<Task> tasks = new ArrayList<>(taskRepository.findAll(
                specification,
                PageRequest.of(0, MY_WORK_FETCH, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.desc("updatedAt"))))
                .getContent());
        tasks.sort(OPEN_TASK_ORDER);

        List<TaskResponse> overdue = new ArrayList<>();
        List<TaskResponse> dueToday = new ArrayList<>();
        List<TaskResponse> inProgress = new ArrayList<>();
        List<TaskResponse> upNext = new ArrayList<>();
        for (Task task : tasks) {
            TaskResponse item = TaskResponse.fromEntity(task);
            if (task.getDueDate() != null && task.getDueDate().isBefore(today)) {
                overdue.add(item);
            } else if (today.equals(task.getDueDate())) {
                dueToday.add(item);
            } else if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                inProgress.add(item);
            } else {
                upNext.add(item);
            }
        }
        return new MyWorkResponse(
                today,
                take(overdue, MY_WORK_SECTION),
                take(dueToday, MY_WORK_SECTION),
                take(inProgress, MY_WORK_SECTION),
                take(upNext, MY_WORK_SECTION));
    }

    @Transactional(readOnly = true)
    public TaskBoardResponse board(UUID organizationId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        return new TaskBoardResponse(
                boardColumn(organizationId, TaskStatus.TODO, BOARD_OPEN_LIMIT, true),
                boardColumn(organizationId, TaskStatus.IN_PROGRESS, BOARD_OPEN_LIMIT, true),
                boardColumn(organizationId, TaskStatus.DONE, BOARD_CLOSED_LIMIT, false),
                boardColumn(organizationId, TaskStatus.CANCELLED, BOARD_CLOSED_LIMIT, false));
    }

    private List<TaskResponse> boardColumn(UUID organizationId, TaskStatus status, int limit, boolean open) {
        Specification<Task> specification = TaskSpecifications.inOrganization(organizationId)
                .and(TaskSpecifications.hasStatus(status));
        Sort sort = open
                ? Sort.by(Sort.Order.asc("dueDate"), Sort.Order.desc("updatedAt"))
                : Sort.by(Sort.Order.desc("updatedAt"));
        List<Task> tasks = new ArrayList<>(taskRepository.findAll(specification, PageRequest.of(0, limit, sort)).getContent());
        if (open) {
            tasks.sort(OPEN_TASK_ORDER);
        }
        return tasks.stream().map(TaskResponse::fromEntity).toList();
    }

    private static List<TaskResponse> take(List<TaskResponse> items, int limit) {
        if (items.size() <= limit) {
            return List.copyOf(items);
        }
        return List.copyOf(items.subList(0, limit));
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
            if (savedAssigneeChanged(previousAssigneeId, task)) {
                publishAssignment(organizationId, authorized.membership().getUser().getId(), task);
            }
        }
        if (request.isRecurrencePresent()) {
            TaskRecurrence recurrence = request.getRecurrence() == null ? TaskRecurrence.NONE : request.getRecurrence();
            if (recurrence != task.getRecurrence()) {
                fieldsChanged = true;
            }
            task.setRecurrence(recurrence);
        }
        Task saved = taskRepository.save(task);
        if (statusChanged && saved.getStatus() == TaskStatus.DONE) {
            activityRecorder.record(
                    authorized.membership().getOrganization(),
                    authorized.membership().getUser(),
                    saved.getId(),
                    ActivityType.TASK_COMPLETED,
                    "completed \"" + saved.getTitle() + "\"");
            spawnNextOccurrence(saved, authorized.membership());
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

    private void spawnNextOccurrence(Task completed, OrganizationMembership membership) {
        TaskRecurrence recurrence = completed.getRecurrence() == null ? TaskRecurrence.NONE : completed.getRecurrence();
        if (recurrence == TaskRecurrence.NONE) {
            return;
        }
        if (taskRepository.existsBySpawnedFromId(completed.getId())) {
            return;
        }
        LocalDate baseDue = completed.getDueDate() == null
                ? LocalDate.now(ZoneOffset.UTC)
                : completed.getDueDate();
        Task next = new Task();
        next.setOrganization(completed.getOrganization());
        next.setCreatedBy(membership.getUser());
        next.setTitle(completed.getTitle());
        next.setDescription(completed.getDescription());
        next.setStatus(TaskStatus.TODO);
        next.setPriority(completed.getPriority());
        next.setDueDate(recurrence.nextDueDate(baseDue));
        next.setRecurrence(recurrence);
        next.setSpawnedFromId(completed.getId());
        next.setAssignee(completed.getAssignee());
        Task savedNext = taskRepository.save(next);
        activityRecorder.record(
                membership.getOrganization(),
                membership.getUser(),
                savedNext.getId(),
                ActivityType.TASK_CREATED,
                "created the next \"" + savedNext.getTitle() + "\"");
        if (savedNext.getAssignee() != null) {
            publishAssignment(
                    membership.getOrganization().getId(),
                    membership.getUser().getId(),
                    savedNext);
        }
    }

    private void publishAssignment(UUID organizationId, UUID actorId, Task task) {
        eventPublisher.publishEvent(new WorkflowTrigger(
                organizationId,
                WorkflowEventType.TASK_ASSIGNED,
                actorId,
                task.getId(),
                task.getAssignee().getId(),
                null,
                task.getDueDate(),
                0));
    }

    private static boolean savedAssigneeChanged(UUID previousAssigneeId, Task task) {
        UUID nextAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        return nextAssigneeId != null && !nextAssigneeId.equals(previousAssigneeId);
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
