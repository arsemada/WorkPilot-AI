package com.workpilot.ai.service.ai;

public record AiCompletion(String content, int promptTokens, int completionTokens) {
}
