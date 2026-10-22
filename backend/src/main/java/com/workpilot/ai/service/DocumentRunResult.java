package com.workpilot.ai.service;

public record DocumentRunResult(Kind kind, String message, boolean summarize) {
    public enum Kind {
        SUCCEEDED,
        FAILED,
        SKIPPED
    }
}
