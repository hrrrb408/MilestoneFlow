package com.milestoneflow.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new workspace.
 */
@Schema(description = "Request body for creating a new workspace")
public record CreateWorkspaceRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 120, message = "Name must be between 1 and 120 characters")
        @Schema(description = "Workspace display name", example = "My Workspace")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(min = 3, max = 50, message = "Slug must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$",
                message = "Slug must contain only lowercase letters, numbers, and hyphens, "
                        + "cannot start or end with a hyphen")
        @Schema(description = "URL-friendly unique identifier", example = "my-workspace")
        String slug,

        @Size(max = 64, message = "Timezone must be at most 64 characters")
        @Schema(description = "IANA timezone ID", example = "Asia/Taipei", defaultValue = "Asia/Taipei")
        String timezone,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code")
        @Schema(description = "3-letter uppercase currency code", example = "TWD", defaultValue = "TWD")
        String defaultCurrency
) {
}
