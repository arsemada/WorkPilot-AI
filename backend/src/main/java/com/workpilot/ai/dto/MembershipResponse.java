package com.workpilot.ai.dto;

import com.workpilot.ai.domain.OrganizationMembership;
import com.workpilot.ai.domain.OrganizationRole;
import com.workpilot.ai.domain.Permission;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        UUID userId,
        String userEmail,
        String userFullName,
        OrganizationRole role,
        Set<Permission> permissions,
        Instant createdAt
) {
    public static MembershipResponse fromEntity(OrganizationMembership m) {
        return new MembershipResponse(
                m.getId(),
                m.getOrganization().getId(),
                m.getOrganization().getName(),
                m.getOrganization().getSlug(),
                m.getUser().getId(),
                m.getUser().getEmail(),
                m.getUser().getFullName(),
                m.getRole(),
                m.getRole().getPermissions(),
                m.getCreatedAt()
        );
    }
}
