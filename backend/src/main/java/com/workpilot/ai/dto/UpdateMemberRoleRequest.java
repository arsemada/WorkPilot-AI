package com.workpilot.ai.dto;

import com.workpilot.ai.domain.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        OrganizationRole role
) {
}
