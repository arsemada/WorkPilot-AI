package com.workpilot.ai.dto;

public record CsrfResponse(String token, String headerName, String parameterName) {
}
