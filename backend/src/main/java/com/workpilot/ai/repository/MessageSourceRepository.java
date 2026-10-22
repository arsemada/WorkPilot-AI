package com.workpilot.ai.repository;

import com.workpilot.ai.domain.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageSourceRepository extends JpaRepository<MessageSource, UUID> {

    List<MessageSource> findByMessageIdIn(Collection<UUID> messageIds);
}
