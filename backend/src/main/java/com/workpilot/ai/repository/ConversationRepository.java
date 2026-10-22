package com.workpilot.ai.repository;

import com.workpilot.ai.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByOrganizationIdAndUserIdOrderByUpdatedAtDesc(UUID organizationId, UUID userId);

    Optional<Conversation> findByIdAndOrganizationIdAndUserId(UUID id, UUID organizationId, UUID userId);
}
