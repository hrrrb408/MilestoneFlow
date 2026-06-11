package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.in.GetCurrentWorkspaceUseCase;
import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.application.result.WorkspaceResult;
import com.milestoneflow.workspace.domain.exception.WorkspaceNotFoundException;
import com.milestoneflow.workspace.domain.model.Workspace;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service for retrieving the current user's active workspace.
 *
 * <p>Finds the user's ACTIVE membership, then loads the corresponding workspace.
 * Returns empty if the user has no active workspace — does not auto-create.
 */
@org.springframework.stereotype.Service
public class GetCurrentWorkspaceService implements GetCurrentWorkspaceUseCase {

    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceRepository workspaceRepository;

    public GetCurrentWorkspaceService(WorkspaceMembershipRepository membershipRepository,
                                      WorkspaceRepository workspaceRepository) {
        this.membershipRepository = membershipRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<WorkspaceResult> getCurrentWorkspace(UUID userId) {
        Optional<WorkspaceMembership> membership = membershipRepository.findActiveByUserId(userId);

        if (membership.isEmpty()) {
            return Optional.empty();
        }

        WorkspaceMembership m = membership.get();
        Workspace workspace = workspaceRepository.findById(m.getWorkspaceId())
                .orElseThrow(WorkspaceNotFoundException::new);

        return Optional.of(new WorkspaceResult(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getStatus().name(),
                workspace.getTimezone(),
                workspace.getDefaultCurrency(),
                m.getRole(),
                workspace.getCreatedAt()
        ));
    }
}
