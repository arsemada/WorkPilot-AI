package com.workpilot.ai.service;

import java.util.List;

public record AgentResult(String content, int promptTokens, int completionTokens, List<AgentToolOutcome> tools) {
}
