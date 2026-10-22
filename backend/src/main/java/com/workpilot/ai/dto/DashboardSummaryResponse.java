package com.workpilot.ai.dto;

import java.util.List;
import java.util.UUID;

public record DashboardSummaryResponse(
        UUID organizationId,
        String organizationName,
        long memberCount,
        long totalTasks,
        long todoTasks,
        long inProgressTasks,
        long completedTasks,
        long cancelledTasks,
        long overdueTasks,
        List<TaskResponse> recentTasks
) {
}
