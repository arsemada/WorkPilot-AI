package com.workpilot.ai.exception;

public class UsageLimitException extends RuntimeException {

    public UsageLimitException(String message) {
        super(message);
    }
}
