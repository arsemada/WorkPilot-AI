package com.workpilot.ai.dto;

import java.util.List;

public record TaskBoardResponse(
        List<TaskResponse> todo,
        List<TaskResponse> inProgress,
        List<TaskResponse> done,
        List<TaskResponse> cancelled
) {
}
