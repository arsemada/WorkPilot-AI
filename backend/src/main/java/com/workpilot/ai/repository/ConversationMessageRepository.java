package com.workpilot.ai.repository;

import com.workpilot.ai.domain.ConversationMessage;
import com.workpilot.ai.domain.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @Query("""
            SELECT COUNT(message) FROM ConversationMessage message
            WHERE message.conversation.organization.id = :organizationId
              AND message.conversation.user.id = :userId
              AND message.role = :role
              AND message.createdAt >= :start
            """)
    long countUserMessagesSince(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId,
            @Param("role") MessageRole role,
            @Param("start") Instant start);
}
