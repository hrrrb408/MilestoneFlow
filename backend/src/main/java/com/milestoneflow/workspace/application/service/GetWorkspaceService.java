package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.in.GetWorkspaceUseCase;
import com.milestoneflow.workspace.application.result.WorkspaceResult;
import com.milestoneflow.workspace.domain.exception.WorkspaceNotFoundException;
import com.milestoneflow.workspace.domain.model.Workspace;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application service for retrieving workspace details by ID.
 *
 * <p>Requires the caller to have an ACTIVE membership in the workspace.
 * Non-members receive 404 (not 403) to prevent workspace existence leakage.
 */
@Service
public class GetWorkspaceService implements GetWorkspaceUseCase {

    private final WorkspaceAccessChecker accessChecker;

    public GetWorkspaceService(WorkspaceAccessChecker accessChecker) {
        this.accessChecker = accessChecker;
    }

    @Override
    public WorkspaceResult getWorkspace(UUID workspaceId, UUID userId) {
        // Check membership — throws WorkspaceAccessDeniedException if not a member
        WorkspaceMembership membership = accessChecker.requireActiveMember(workspaceId, userId);

        // Load workspace — should always exist if membership exists
        // But use the same not-found message to avoid information leakage
        Workspace workspace = accessChecker.findWorkspaceOrThrow(workspaceId);

        return new WorkspaceResult(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getStatus().name(),
                workspace.getTimezone(),
                workspace.getDefaultCurrency(),
                membership.getRole(),
                workspace.getCreatedAt()
        );
    }
}
