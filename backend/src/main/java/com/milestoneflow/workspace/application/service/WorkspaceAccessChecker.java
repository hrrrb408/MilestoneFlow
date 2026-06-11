package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.domain.exception.WorkspaceAccessDeniedException;
import com.milestoneflow.workspace.domain.exception.WorkspaceNotFoundException;
import com.milestoneflow.workspace.domain.exception.WorkspaceOwnerRequiredException;
import com.milestoneflow.workspace.domain.model.Workspace;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import com.milestoneflow.workspace.domain.type.WorkspaceMembershipRole;

import java.util.UUID;

/**
 * Application service for workspace access control checks.
 *
 * <p>Provides reusable authorization checks for workspace-scoped operations.
 * All methods verify ACTIVE membership; OWNER-specific checks additionally
 * verify the role.
 *
 * <p>V0.1 only supports OWNER role. Non-members and non-active memberships
 * are denied access.
 */
@org.springframework.stereotype.Service
public class WorkspaceAccessChecker {

    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceAccessChecker(WorkspaceMembershipRepository membershipRepository,
                                  WorkspaceRepository workspaceRepository) {
        this.membershipRepository = membershipRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Verifies that the user has an ACTIVE membership in the workspace.
     *
     * @param workspaceId the workspace to check
     * @param userId      the user to check
     * @return the active membership
     * @throws WorkspaceAccessDeniedException if no active membership exists
     */
    public WorkspaceMembership requireActiveMember(UUID workspaceId, UUID userId) {
        return membershipRepository.findActiveByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(WorkspaceAccessDeniedException::new);
    }

    /**
     * Verifies that the user is the OWNER of the workspace.
     *
     * @param workspaceId the workspace to check
     * @param userId      the user to check
     * @return the active OWNER membership
     * @throws WorkspaceAccessDeniedException if no active membership exists
     * @throws WorkspaceOwnerRequiredException if active membership exists but is not OWNER
     */
    public WorkspaceMembership requireOwner(UUID workspaceId, UUID userId) {
        WorkspaceMembership membership = requireActiveMember(workspaceId, userId);
        if (membership.getRole() != WorkspaceMembershipRole.OWNER) {
            throw new WorkspaceOwnerRequiredException();
        }
        return membership;
    }

    /**
     * Finds a workspace by ID or throws WorkspaceNotFoundException.
     *
     * <p>Uses the same "not found" message regardless of whether the workspace
     * does not exist or the user lacks access, to prevent information leakage.
     *
     * @param workspaceId the workspace to find
     * @return the workspace
     * @throws WorkspaceNotFoundException if not found
     */
    public Workspace findWorkspaceOrThrow(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
    }
}
