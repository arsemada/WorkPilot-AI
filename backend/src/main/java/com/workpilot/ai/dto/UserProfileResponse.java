package com.workpilot.ai.dto;

import com.workpilot.ai.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        List<MembershipResponse> memberships
) {
    public static UserProfileResponse of(User user, List<MembershipResponse> memberships) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFullName(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                memberships != null ? memberships : List.of()
        );
    }
}
