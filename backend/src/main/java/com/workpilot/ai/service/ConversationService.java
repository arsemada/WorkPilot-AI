package com.workpilot.ai.service;

import com.workpilot.ai.config.AiProperties;
import com.workpilot.ai.domain.Conversation;
import com.workpilot.ai.domain.ConversationMessage;
import com.workpilot.ai.domain.MessageRole;
import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.Permission;
import com.workpilot.ai.dto.ConversationDetailResponse;
import com.workpilot.ai.dto.ConversationMessageResponse;
import com.workpilot.ai.dto.ConversationResponse;
import com.workpilot.ai.exception.AiUnavailableException;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.exception.UsageLimitException;
import com.workpilot.ai.repository.ConversationMessageRepository;
import com.workpilot.ai.repository.ConversationRepository;
import com.workpilot.ai.security.OrganizationAccessService;
import com.workpilot.ai.service.ai.AiChatMessage;
import com.workpilot.ai.service.ai.AiCompletion;
import com.workpilot.ai.service.ai.AiCompletionClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    static final String DEFAULT_TITLE = "New conversation";
    static final String SYSTEM_PROMPT = """
            You are the WorkPilot assistant. Answer the user's question clearly.
            You only know what appears in this conversation. You cannot see the organization's documents, tasks, or other records.
            """;
    private static final int STORED_CONTENT_LIMIT = 8000;
    private static final int LIST_LIMIT = 50;

    private final OrganizationAccessService accessService;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AiCompletionClient completionClient;
    private final AiProperties aiProperties;
    private final TransactionTemplate readTemplate;
    private final TransactionTemplate writeTemplate;

    public ConversationService(
            OrganizationAccessService accessService,
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            AiCompletionClient completionClient,
            AiProperties aiProperties,
            PlatformTransactionManager transactionManager) {
        this.accessService = accessService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.completionClient = completionClient;
        this.aiProperties = aiProperties;
        this.readTemplate = new TransactionTemplate(transactionManager);
        this.readTemplate.setReadOnly(true);
        this.writeTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ConversationResponse create(UUID organizationId, String title) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setOrganization(membership.getOrganization());
        conversation.setUser(membership.getUser());
        conversation.setTitle(normalizeTitle(title));
        return ConversationResponse.fromEntity(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID organizationId) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        return conversationRepository
                .findByOrganizationIdAndUserIdOrderByUpdatedAtDesc(organizationId, membership.getUser().getId())
                .stream()
                .limit(LIST_LIMIT)
                .map(ConversationResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse get(UUID organizationId, UUID conversationId) {
        Conversation conversation = requireOwnConversation(organizationId, conversationId);
        return detail(conversation);
    }

    public ConversationDetailResponse send(UUID organizationId, UUID conversationId, String content) {
        PreparedSend prepared = readTemplate.execute(status -> prepare(organizationId, conversationId, content));
        if (prepared == null) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        AiCompletion completion = completionClient.complete(prepared.messages());
        if (completion == null || completion.content() == null || completion.content().isBlank()) {
            throw new AiUnavailableException("The AI assistant is unavailable.");
        }
        if (completion.content().length() > STORED_CONTENT_LIMIT) {
            throw new AiUnavailableException("The AI assistant is unavailable.");
        }
        ConversationDetailResponse saved = writeTemplate.execute(status -> persist(prepared, completion));
        if (saved == null) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return saved;
    }

    @Transactional
    public void delete(UUID organizationId, UUID conversationId) {
        Conversation conversation = requireOwnConversation(organizationId, conversationId);
        conversationRepository.delete(conversation);
    }

    private PreparedSend prepare(UUID organizationId, UUID conversationId, String content) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        String message = content == null ? "" : content.trim();
        if (message.isEmpty()) {
            throw new BadRequestException("Message is required");
        }
        if (message.length() > aiProperties.getMaxMessageChars()) {
            throw new BadRequestException("Message must be " + aiProperties.getMaxMessageChars() + " characters or fewer");
        }
        UUID userId = membership.getUser().getId();
        Conversation conversation = conversationRepository
                .findByIdAndOrganizationIdAndUserId(conversationId, organizationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        long sentToday = messageRepository.countUserMessagesSince(organizationId, userId, MessageRole.USER, startOfDay);
        if (sentToday >= aiProperties.getDailyMessageLimit()) {
            throw new UsageLimitException("You have reached today's assistant message limit.");
        }
        List<ConversationMessage> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        int historyLimit = Math.max(0, aiProperties.getMaxHistoryMessages());
        if (history.size() > historyLimit) {
            history = history.subList(history.size() - historyLimit, history.size());
        }
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatMessage("system", SYSTEM_PROMPT));
        for (ConversationMessage previous : history) {
            messages.add(new AiChatMessage(previous.getRole() == MessageRole.USER ? "user" : "assistant", previous.getContent()));
        }
        messages.add(new AiChatMessage("user", message));
        boolean rename = history.isEmpty() || DEFAULT_TITLE.equals(conversation.getTitle());
        return new PreparedSend(organizationId, userId, conversation.getId(), message, rename, messages);
    }

    private ConversationDetailResponse persist(PreparedSend prepared, AiCompletion completion) {
        Conversation conversation = conversationRepository
                .findByIdAndOrganizationIdAndUserId(prepared.conversationId(), prepared.organizationId(), prepared.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        long sentToday = messageRepository.countUserMessagesSince(
                prepared.organizationId(), prepared.userId(), MessageRole.USER, startOfDay);
        if (sentToday >= aiProperties.getDailyMessageLimit()) {
            throw new UsageLimitException("You have reached today's assistant message limit.");
        }
        Instant now = Instant.now();
        ConversationMessage userMessage = new ConversationMessage();
        userMessage.setId(UUID.randomUUID());
        userMessage.setConversation(conversation);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(prepared.content());
        userMessage.setCreatedAt(now);
        messageRepository.save(userMessage);

        ConversationMessage assistantMessage = new ConversationMessage();
        assistantMessage.setId(UUID.randomUUID());
        assistantMessage.setConversation(conversation);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent(completion.content());
        assistantMessage.setPromptTokens(completion.promptTokens());
        assistantMessage.setCompletionTokens(completion.completionTokens());
        assistantMessage.setCreatedAt(now.plusMillis(1));
        messageRepository.save(assistantMessage);

        if (prepared.rename()) {
            conversation.setTitle(titleFromMessage(prepared.content()));
        }
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);
        return detail(conversation);
    }

    private Conversation requireOwnConversation(UUID organizationId, UUID conversationId) {
        OrganizationMembership membership = accessService.requirePermission(organizationId, Permission.RESOURCE_READ);
        return conversationRepository
                .findByIdAndOrganizationIdAndUserId(conversationId, organizationId, membership.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private ConversationDetailResponse detail(Conversation conversation) {
        List<ConversationMessageResponse> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(ConversationMessageResponse::fromEntity)
                .toList();
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.scopedOrganizationId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messages
        );
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return DEFAULT_TITLE;
        }
        String trimmed = title.trim().replaceAll("\\s+", " ");
        return trimmed.isEmpty() ? DEFAULT_TITLE : trimmed;
    }

    private static String titleFromMessage(String content) {
        String collapsed = content.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= 80) {
            return collapsed;
        }
        return collapsed.substring(0, 80).trim();
    }

    private record PreparedSend(
            UUID organizationId,
            UUID userId,
            UUID conversationId,
            String content,
            boolean rename,
            List<AiChatMessage> messages
    ) {
    }
}
