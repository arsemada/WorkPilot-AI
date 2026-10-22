package com.workpilot.ai.service;

import com.workpilot.ai.config.AiProperties;
import com.workpilot.ai.exception.AiUnavailableException;
import com.workpilot.ai.service.ai.AiChatMessage;
import com.workpilot.ai.service.ai.AiCompletion;
import com.workpilot.ai.service.ai.AiCompletionClient;
import com.workpilot.ai.service.ai.AiToolCall;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssistantAgent {

    private static final String UNAVAILABLE = "The AI assistant is unavailable.";
    private static final String TIMED_OUT = "The AI assistant did not respond in time.";
    private static final int STORED_CONTENT_LIMIT = 8000;

    private final AiCompletionClient completionClient;
    private final TaskAssistantTools taskTools;
    private final AiProperties aiProperties;

    public AssistantAgent(AiCompletionClient completionClient, TaskAssistantTools taskTools, AiProperties aiProperties) {
        this.completionClient = completionClient;
        this.taskTools = taskTools;
        this.aiProperties = aiProperties;
    }

    public AgentResult run(UUID organizationId, UUID userId, List<AiChatMessage> messages) {
        Instant deadline = Instant.now().plus(budget());
        List<AiChatMessage> transcript = new ArrayList<>(messages);
        List<AgentToolOutcome> outcomes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int promptTokens = 0;
        int completionTokens = 0;
        int calls = 0;
        String content = "";
        boolean proposed = false;
        int maxCalls = Math.max(1, aiProperties.getMaxToolCalls());

        for (int round = 0; round <= maxCalls; round++) {
            ensureTime(deadline);
            boolean allowTools = !proposed && calls < maxCalls;
            AiCompletion completion = completionClient.complete(transcript, allowTools ? taskTools.specs() : List.of());
            promptTokens += completion.promptTokens();
            completionTokens += completion.completionTokens();
            content = completion.content();
            if (completion.toolCalls().isEmpty()) {
                break;
            }
            if (!allowTools) {
                break;
            }
            transcript.add(new AiChatMessage("assistant", content, null, completion.toolCalls()));
            boolean followUp = false;
            for (AiToolCall call : completion.toolCalls()) {
                ensureTime(deadline);
                if (calls >= maxCalls) {
                    transcript.add(new AiChatMessage("tool", "{\"error\":\"The tool call limit was reached.\"}", call.id(), List.of()));
                    followUp = true;
                    break;
                }
                calls++;
                String fingerprint = call.name() + "\n" + (call.arguments() == null ? "" : call.arguments());
                boolean duplicate = !seen.add(fingerprint);
                TaskAssistantTools.ToolHandling handling = taskTools.handle(call, organizationId, userId, duplicate);
                if (handling.outcome() != null) {
                    outcomes.add(handling.outcome());
                }
                if (handling.propose()) {
                    proposed = true;
                    if (content.isBlank() && handling.proposalText() != null) {
                        content = handling.proposalText();
                    }
                    break;
                }
                transcript.add(new AiChatMessage("tool", handling.modelContent() == null ? "{}" : handling.modelContent(), call.id(), List.of()));
                followUp = true;
            }
            if (proposed) {
                break;
            }
            if (!followUp) {
                break;
            }
        }
        if (content.isBlank() && outcomes.stream().anyMatch(outcome -> outcome.name().equals(TaskAssistantTools.LIST_TASKS))) {
            content = "Here are the tasks that matched.";
        }
        if (content.isBlank() || content.length() > STORED_CONTENT_LIMIT) {
            throw new AiUnavailableException(UNAVAILABLE);
        }
        return new AgentResult(content, promptTokens, completionTokens, List.copyOf(outcomes));
    }

    public ToolConfirmation executeConfirmed(String argumentsJson, UUID organizationId) {
        TaskAssistantTools.ToolHandling handling = taskTools.executeConfirmed(argumentsJson, organizationId);
        return new ToolConfirmation(handling.outcome(), handling.proposalText());
    }

    private Duration budget() {
        Duration configured = aiProperties.getToolBudget();
        return configured == null || configured.isNegative() || configured.isZero() ? Duration.ofSeconds(15) : configured;
    }

    private static void ensureTime(Instant deadline) {
        if (Instant.now().isAfter(deadline)) {
            throw new AiUnavailableException(TIMED_OUT);
        }
    }

    public record ToolConfirmation(AgentToolOutcome outcome, String message) {
    }
}
