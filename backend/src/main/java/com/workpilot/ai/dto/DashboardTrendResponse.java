package com.workpilot.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardTrendResponse(
        LocalDate from,
        LocalDate to,
        List<DashboardTrendPoint> points
) {
    public record DashboardTrendPoint(LocalDate date, long completedCount) {
    }
}
