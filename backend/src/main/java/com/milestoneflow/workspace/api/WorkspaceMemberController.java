package com.milestoneflow.workspace.api;

import com.milestoneflow.shared.api.ApiResponse;
import com.milestoneflow.shared.api.ApiErrorResponse;
import com.milestoneflow.shared.infrastructure.security.CurrentUserPrincipal;
import com.milestoneflow.workspace.api.dto.CurrentWorkspaceMembershipResponse;
import com.milestoneflow.workspace.api.dto.WorkspaceMemberResponse;
import com.milestoneflow.workspace.api.dto.WorkspaceMembersResponse;
import com.milestoneflow.workspace.application.port.in.GetCurrentWorkspaceMembershipUseCase;
import com.milestoneflow.workspace.application.port.in.ListWorkspaceMembersUseCase;
import com.milestoneflow.workspace.application.result.CurrentWorkspaceMembershipResult;
import com.milestoneflow.workspace.application.result.WorkspaceMemberResult;
import com.milestoneflow.workspace.application.result.WorkspaceMembersResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for workspace membership read operations.
 *
 * <p>Exposes the member roster query and the current-user membership query.
 * All endpoints require authentication via the MF_ACCESS cookie and an
 * ACTIVE membership in the target workspace.
 *
 * <p>Non-members receive 404 (not 403) to prevent workspace existence leakage,
 * consistent with the rest of the workspace API (see MF-BE-B2-001 §9).
 *
 * <p>Controllers depend only on use case ports (application layer), never on
 * repositories, infrastructure, or domain entities directly.
 */
@RestController
@RequestMapping("/workspaces")
@Tag(name = "Workspace Members", description = "Workspace membership read APIs")
@SecurityRequirement(name = "cookieAuth")
public class WorkspaceMemberController {

    private final ListWorkspaceMembersUseCase listWorkspaceMembersUseCase;
    private final GetCurrentWorkspaceMembershipUseCase getCurrentWorkspaceMembershipUseCase;

    public WorkspaceMemberController(ListWorkspaceMembersUseCase listWorkspaceMembersUseCase,
                                     GetCurrentWorkspaceMembershipUseCase getCurrentWorkspaceMembershipUseCase) {
        this.listWorkspaceMembersUseCase = listWorkspaceMembersUseCase;
        this.getCurrentWorkspaceMembershipUseCase = getCurrentWorkspaceMembershipUseCase;
    }

    /**
     * Lists the ACTIVE members of a workspace.
     */
    @Operation(summary = "List workspace members",
            description = "Returns the ACTIVE members of a workspace, ordered by joinedAt ascending. "
                    + "The authenticated user must have an ACTIVE membership in the workspace. "
                    + "REMOVED and PENDING memberships are excluded. "
                    + "Member responses expose only safe display fields (userId, email, displayName, "
                    + "role, status, joinedAt) — never passwordHash, emailNormalized, or token material.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Member roster returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found or access denied",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<ApiResponse<WorkspaceMembersResponse>> listMembers(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId) {

        WorkspaceMembersResult result = listWorkspaceMembersUseCase.listMembers(
                workspaceId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    /**
     * Returns the current user's membership in a workspace.
     */
    @Operation(summary = "Get current user's membership",
            description = "Returns the authenticated user's membership (role, status, joinedAt) in a workspace. "
                    + "The user must have an ACTIVE membership. "
                    + "Carries no email/displayName and no sensitive fields.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Current membership returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Workspace not found or no active membership",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{workspaceId}/members/me")
    public ResponseEntity<ApiResponse<CurrentWorkspaceMembershipResponse>> currentMembership(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable UUID workspaceId) {

        CurrentWorkspaceMembershipResult result = getCurrentWorkspaceMembershipUseCase.getCurrentMembership(
                workspaceId, principal.userId(), resolveRequestId());

        return ResponseEntity.ok(ApiResponse.of(toResponse(result), resolveRequestId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static WorkspaceMembersResponse toResponse(WorkspaceMembersResult result) {
        List<WorkspaceMemberResponse> members = result.members().stream()
                .map(WorkspaceMemberController::toMemberResponse)
                .toList();
        return new WorkspaceMembersResponse(result.workspaceId().toString(), members);
    }

    private static WorkspaceMemberResponse toMemberResponse(WorkspaceMemberResult member) {
        return new WorkspaceMemberResponse(
                member.userId().toString(),
                member.email(),
                member.displayName(),
                member.role(),
                member.status(),
                member.joinedAt() != null ? member.joinedAt().toString() : null
        );
    }

    private static CurrentWorkspaceMembershipResponse toResponse(CurrentWorkspaceMembershipResult result) {
        return new CurrentWorkspaceMembershipResponse(
                result.workspaceId().toString(),
                result.userId().toString(),
                result.role(),
                result.status(),
                result.joinedAt() != null ? result.joinedAt().toString() : null
        );
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId != null ? requestId : "unknown";
    }
}
