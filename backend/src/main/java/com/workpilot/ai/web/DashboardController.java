package com.workpilot.ai.web;

import com.workpilot.ai.dto.ActivityEventResponse;
import com.workpilot.ai.dto.DashboardSummaryResponse;
import com.workpilot.ai.dto.DashboardTrendResponse;
import com.workpilot.ai.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Summarize tasks for an organization")
    public DashboardSummaryResponse summary(@PathVariable UUID organizationId) {
        return dashboardService.summary(organizationId);
    }

    @GetMapping("/trends")
    @Operation(summary = "Count tasks marked done on each day in a UTC date range")
    public DashboardTrendResponse trends(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return dashboardService.trends(organizationId, from, to);
    }

    @GetMapping("/activity")
    @Operation(summary = "List recent task activity for an organization")
    public List<ActivityEventResponse> activity(@PathVariable UUID organizationId) {
        return dashboardService.activity(organizationId);
    }
}
