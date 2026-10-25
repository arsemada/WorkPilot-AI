package com.workpilot.ai.domain;

import java.time.LocalDate;

public enum TaskRecurrence {
    NONE,
    WEEKLY,
    MONTHLY;

    public LocalDate nextDueDate(LocalDate base) {
        if (this == NONE || base == null) {
            return null;
        }
        return switch (this) {
            case WEEKLY -> base.plusWeeks(1);
            case MONTHLY -> base.plusMonths(1);
            case NONE -> null;
        };
    }
}
