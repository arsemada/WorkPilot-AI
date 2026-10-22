package com.workpilot.ai.repository;

import com.workpilot.ai.domain.AssistantToolCall;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistantToolCallRepository extends JpaRepository<AssistantToolCall, UUID> {

    List<AssistantToolCall> findByMessageIdIn(Collection<UUID> messageIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select toolCall from AssistantToolCall toolCall where toolCall.id = :id")
    Optional<AssistantToolCall> lockById(@Param("id") UUID id);
}
