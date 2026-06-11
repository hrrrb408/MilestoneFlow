package com.milestoneflow.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating workspace basic information.
 *
 * <p>All fields are optional — only non-null fields are updated.
 * V0.1 does not allow slug changes.
 */
@Schema(description = "Request body for updating workspace basic info")
public record UpdateWorkspaceRequest(

        @Size(min = 1, max = 120, message = "Name must be between 1 and 120 characters")
        @Schema(description = "New workspace display name", example = "New Workspace Name")
        String name,

        @Size(max = 64, message = "Timezone must be at most 64 characters")
        @Schema(description = "New IANA timezone ID", example = "Asia/Taipei")
        String timezone,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code")
        @Schema(description = "New 3-letter uppercase currency code", example = "TWD")
        String defaultCurrency
) {
}
