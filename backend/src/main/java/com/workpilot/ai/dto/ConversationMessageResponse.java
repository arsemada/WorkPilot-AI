package com.workpilot.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.domain.AssistantToolCall;
import com.workpilot.ai.domain.ConversationMessage;
import com.workpilot.ai.domain.MessageRole;
import com.workpilot.ai.domain.MessageSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationMessageResponse(
        UUID id,
        MessageRole role,
        String content,
        Boolean documentSupport,
        List<MessageSourceResponse> sources,
        List<AssistantToolCallResponse> toolCalls,
        Instant createdAt
) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static ConversationMessageResponse fromEntity(
            ConversationMessage message,
            List<MessageSource> sources,
            List<AssistantToolCall> toolCalls) {
        List<MessageSourceResponse> references = sources == null
                ? List.of()
                : sources.stream().map(ConversationMessageResponse::source).toList();
        List<AssistantToolCallResponse> calls = toolCalls == null
                ? List.of()
                : toolCalls.stream().map(ConversationMessageResponse::toolCall).toList();
        return new ConversationMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getDocumentSupport(),
                references,
                calls,
                message.getCreatedAt()
        );
    }

    private static MessageSourceResponse source(MessageSource source) {
        return new MessageSourceResponse(
                source.getDocumentId(),
                source.getDocumentTitle(),
                source.getChunkIndex(),
                source.getPageNumber(),
                source.getSectionLabel(),
                source.getSnippet()
        );
    }

    private static AssistantToolCallResponse toolCall(AssistantToolCall toolCall) {
        return new AssistantToolCallResponse(
                toolCall.getId(),
                toolCall.getToolName(),
                toolCall.getStatus(),
                json(toolCall.getArgumentsJson()),
                json(toolCall.getResultJson()),
                toolCall.getErrorMessage()
        );
    }

    private static JsonNode json(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(value);
        } catch (Exception exception) {
            return null;
        }
    }
}
