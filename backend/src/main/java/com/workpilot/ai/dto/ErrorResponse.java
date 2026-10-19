package com.workpilot.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, null, path);
    }

    public static ErrorResponse of(int status, String error, String message, Map<String, String> details, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, details, path);
    }
}
