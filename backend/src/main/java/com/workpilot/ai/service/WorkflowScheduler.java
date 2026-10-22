package com.workpilot.ai.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class WorkflowScheduler {

    private final WorkflowEngine engine;

    public WorkflowScheduler(WorkflowEngine engine) {
        this.engine = engine;
    }

    @Scheduled(cron = "${workpilot.workflow.overdue-cron:0 0 8 * * *}", zone = "UTC")
    public void scanOverdueTasks() {
        engine.scanOverdue(LocalDate.now(ZoneOffset.UTC));
    }

    @Scheduled(cron = "${workpilot.workflow.activity-cron:0 15 8 * * *}", zone = "UTC")
    public void sendActivitySummaries() {
        engine.summarizeActivity(LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    @Scheduled(
            fixedDelayString = "${workpilot.workflow.retry-delay-ms:60000}",
            initialDelayString = "${workpilot.workflow.retry-initial-delay-ms:60000}")
    public void retryFailedWorkflows() {
        engine.retryDue();
    }
}
