package com.workpilot.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 255, message = "Organization name must be between 2 and 255 characters")
        String name,

        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        @Size(min = 2, max = 100, message = "Slug must be between 2 and 100 characters")
        String slug
) {
}
