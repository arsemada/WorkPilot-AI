package com.workpilot.ai.dto;

import com.workpilot.ai.domain.Organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationResponse fromEntity(Organization org) {
        return new OrganizationResponse(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }
}
