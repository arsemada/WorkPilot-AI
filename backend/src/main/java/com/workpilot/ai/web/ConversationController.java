package com.workpilot.ai.web;

import com.workpilot.ai.dto.ConversationDetailResponse;
import com.workpilot.ai.dto.ConversationResponse;
import com.workpilot.ai.dto.CreateConversationRequest;
import com.workpilot.ai.dto.SendMessageRequest;
import com.workpilot.ai.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/conversations")
@Tag(name = "Assistant")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a conversation for the signed-in member")
    public ConversationResponse create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        return conversationService.create(organizationId, title);
    }

    @GetMapping
    @Operation(summary = "List the signed-in member's conversations")
    public List<ConversationResponse> list(@PathVariable UUID organizationId) {
        return conversationService.list(organizationId);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Read a conversation and its messages")
    public ConversationDetailResponse get(
            @PathVariable UUID organizationId,
            @PathVariable UUID conversationId) {
        return conversationService.get(organizationId, conversationId);
    }

    @PostMapping("/{conversationId}/messages")
    @Operation(summary = "Send a message and store the assistant reply")
    public ConversationDetailResponse send(
            @PathVariable UUID organizationId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return conversationService.send(organizationId, conversationId, request.content());
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of the signed-in member's conversations")
    public void delete(@PathVariable UUID organizationId, @PathVariable UUID conversationId) {
        conversationService.delete(organizationId, conversationId);
    }
}
