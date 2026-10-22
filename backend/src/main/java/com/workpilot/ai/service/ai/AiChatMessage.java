package com.workpilot.ai.service.ai;

import java.util.List;

public record AiChatMessage(String role, String content, String toolCallId, List<AiToolCall> toolCalls) {

    public AiChatMessage {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public AiChatMessage(String role, String content) {
        this(role, content, null, List.of());
    }
}
