package com.workpilot.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "workpilot.workflow")
public class WorkflowProperties {

    private Duration retryBackoff = Duration.ofMinutes(1);
    private String overdueCron = "0 0 8 * * *";
    private String activityCron = "0 15 8 * * *";
    private Duration retryDelay = Duration.ofSeconds(60);
    private Duration retryInitialDelay = Duration.ofSeconds(60);

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public String getOverdueCron() {
        return overdueCron;
    }

    public void setOverdueCron(String overdueCron) {
        this.overdueCron = overdueCron;
    }

    public String getActivityCron() {
        return activityCron;
    }

    public void setActivityCron(String activityCron) {
        this.activityCron = activityCron;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Duration getRetryInitialDelay() {
        return retryInitialDelay;
    }

    public void setRetryInitialDelay(Duration retryInitialDelay) {
        this.retryInitialDelay = retryInitialDelay;
    }
}
