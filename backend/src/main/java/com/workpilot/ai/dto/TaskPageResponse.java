package com.workpilot.ai.dto;

import java.util.List;

public record TaskPageResponse(
        List<TaskResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
