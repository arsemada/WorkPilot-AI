package com.workpilot.ai.service.ai;

import java.util.List;

public interface AiCompletionClient {

    AiCompletion complete(List<AiChatMessage> messages);
}
