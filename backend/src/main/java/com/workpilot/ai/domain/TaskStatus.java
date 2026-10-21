package com.workpilot.ai.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Task lifecycle.
 * A new task may be created in any status. Later changes must follow these transitions:
 * TODO to IN_PROGRESS or CANCELLED;
 * IN_PROGRESS to TODO, DONE, or CANCELLED;
 * DONE to IN_PROGRESS;
 * CANCELLED to TODO.
 * Keeping the current status is allowed.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    public boolean canTransitionTo(TaskStatus next) {
        if (next == null) {
            return false;
        }
        if (this == next) {
            return true;
        }
        return allowedTargets().contains(next);
    }

    public Set<TaskStatus> allowedTargets() {
        return switch (this) {
            case TODO -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(TODO, DONE, CANCELLED);
            case DONE -> EnumSet.of(IN_PROGRESS);
            case CANCELLED -> EnumSet.of(TODO);
        };
    }
}
