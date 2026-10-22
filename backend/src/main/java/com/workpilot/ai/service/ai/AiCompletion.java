package com.workpilot.ai.service.ai;

import java.util.List;

public record AiCompletion(String content, int promptTokens, int completionTokens, List<AiToolCall> toolCalls) {

    public AiCompletion {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public AiCompletion(String content, int promptTokens, int completionTokens) {
        this(content, promptTokens, completionTokens, List.of());
    }
}
