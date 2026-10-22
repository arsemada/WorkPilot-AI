package com.workpilot.ai.service.ai;

import java.util.List;

public interface AiCompletionClient {

    AiCompletion complete(List<AiChatMessage> messages);

    default AiCompletion complete(List<AiChatMessage> messages, List<AiToolSpec> tools) {
        return complete(messages);
    }
}
