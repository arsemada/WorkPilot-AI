package com.workpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
        String name
) {
}
