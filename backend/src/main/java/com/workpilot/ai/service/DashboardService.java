package com.workpilot.ai.service;

import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.dto.ActivityEventResponse;
import com.workpilot.ai.dto.DashboardSummaryResponse;
import com.workpilot.ai.dto.DashboardTrendResponse;
import com.workpilot.ai.dto.DashboardTrendResponse.DashboardTrendPoint;
import com.workpilot.ai.dto.TaskResponse;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.repository.ActivityEventRepository;
import com.workpilot.ai.repository.OrganizationMembershipRepository;
import com.workpilot.ai.repository.TaskRepository;
import com.workpilot.ai.security.OrganizationAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private static final int RECENT_TASKS = 5;
    private static final int RECENT_ACTIVITY = 10;
    private static final int DEFAULT_TREND_DAYS = 14;
    private static final int MAX_TREND_DAYS = 90;

    private final TaskRepository taskRepository;
    private final ActivityEventRepository activityEventRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationAccessService accessService;

    public DashboardService(
            TaskRepository taskRepository,
            ActivityEventRepository activityEventRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationAccessService accessService) {
        this.taskRepository = taskRepository;
        this.activityEventRepository = activityEventRepository;
        this.membershipRepository = membershipRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(UUID organizationId) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        long todo = 0;
        long inProgress = 0;
        long completed = 0;
        long cancelled = 0;
        long total = 0;
        for (Object[] row : taskRepository.countGroupedByStatus(organizationId)) {
            TaskStatus status = (TaskStatus) row[0];
            long count = (Long) row[1];
            total += count;
            switch (status) {
                case TODO -> todo = count;
                case IN_PROGRESS -> inProgress = count;
                case DONE -> completed = count;
                case CANCELLED -> cancelled = count;
            }
        }
        long overdue = taskRepository.countOverdue(organizationId, LocalDate.now(ZoneOffset.UTC));
        List<TaskResponse> recentTasks = taskRepository
                .findRecentByOrganizationId(organizationId, PageRequest.of(0, RECENT_TASKS))
                .stream()
                .map(TaskResponse::fromEntity)
                .toList();
        return new DashboardSummaryResponse(
                organizationId,
                membership.getOrganization().getName(),
                membershipRepository.countMembers(organizationId),
                total,
                todo,
                inProgress,
                completed,
                cancelled,
                overdue,
                recentTasks
        );
    }

    @Transactional(readOnly = true)
    public DashboardTrendResponse trends(UUID organizationId, LocalDate from, LocalDate to) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from;
        LocalDate end = to;
        if (start == null && end == null) {
            end = today;
            start = today.minusDays(DEFAULT_TREND_DAYS - 1L);
        } else if (start == null || end == null) {
            throw new BadRequestException("Choose a start and end date");
        }
        if (start.isAfter(end)) {
            throw new BadRequestException("The date range is invalid");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (inclusiveDays > MAX_TREND_DAYS) {
            throw new BadRequestException("The date range must be 90 days or fewer");
        }

        Map<LocalDate, Long> completedByDay = new HashMap<>();
        for (Object[] row : activityEventRepository.countByDay(organizationId, ActivityType.TASK_COMPLETED, start, end)) {
            completedByDay.put((LocalDate) row[0], (Long) row[1]);
        }
        List<DashboardTrendPoint> points = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            points.add(new DashboardTrendPoint(day, completedByDay.getOrDefault(day, 0L)));
        }
        return new DashboardTrendResponse(start, end, points);
    }

    @Transactional(readOnly = true)
    public List<ActivityEventResponse> activity(UUID organizationId) {
        accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        return activityEventRepository
                .findRecentByOrganizationId(organizationId, PageRequest.of(0, RECENT_ACTIVITY))
                .stream()
                .map(ActivityEventResponse::fromEntity)
                .toList();
    }
}
