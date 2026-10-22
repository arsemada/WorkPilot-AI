package com.workpilot.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkflowTriggerListener {

    private final WorkflowEngine engine;

    public WorkflowTriggerListener(WorkflowEngine engine) {
        this.engine = engine;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTrigger(WorkflowTrigger trigger) {
        engine.handle(trigger);
    }
}
