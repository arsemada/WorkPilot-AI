package com.workpilot.ai.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusTest {

    @Test
    void allowsTheDocumentedTransitions() {
        assertTrue(TaskStatus.TODO.canTransitionTo(TaskStatus.TODO));
        assertTrue(TaskStatus.TODO.canTransitionTo(TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatus.TODO.canTransitionTo(TaskStatus.CANCELLED));
        assertFalse(TaskStatus.TODO.canTransitionTo(TaskStatus.DONE));

        assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.TODO));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.DONE));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.CANCELLED));

        assertTrue(TaskStatus.DONE.canTransitionTo(TaskStatus.IN_PROGRESS));
        assertFalse(TaskStatus.DONE.canTransitionTo(TaskStatus.CANCELLED));
        assertFalse(TaskStatus.DONE.canTransitionTo(TaskStatus.TODO));

        assertTrue(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.TODO));
        assertFalse(TaskStatus.CANCELLED.canTransitionTo(TaskStatus.DONE));
        assertFalse(TaskStatus.TODO.canTransitionTo(null));
    }
}
