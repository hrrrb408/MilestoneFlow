package com.milestoneflow.workspace.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for the workspace member roster.
 *
 * <p>Wraps the workspace ID and the list of ACTIVE members, ordered by
 * {@code joinedAt} ascending.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Workspace member roster (ACTIVE members only)")
public record WorkspaceMembersResponse(

        @Schema(description = "Workspace identifier", example = "0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b")
        String workspaceId,

        @Schema(description = "ACTIVE members, ordered by joinedAt ascending")
        List<WorkspaceMemberResponse> members
) {
}
