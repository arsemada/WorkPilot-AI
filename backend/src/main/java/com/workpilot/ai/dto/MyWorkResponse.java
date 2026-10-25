package com.workpilot.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record MyWorkResponse(
        LocalDate today,
        List<TaskResponse> overdue,
        List<TaskResponse> dueToday,
        List<TaskResponse> inProgress,
        List<TaskResponse> upNext
) {
}
