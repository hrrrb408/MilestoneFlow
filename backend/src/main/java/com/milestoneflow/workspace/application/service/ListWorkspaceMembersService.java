package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.in.ListWorkspaceMembersUseCase;
import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.port.out.WorkspaceMembershipRepository;
import com.milestoneflow.workspace.application.result.WorkspaceMemberResult;
import com.milestoneflow.workspace.application.result.WorkspaceMembersResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for listing the ACTIVE members of a workspace.
 *
 * <p>Requires the caller to have an ACTIVE membership in the workspace.
 * Non-members receive 404 (not 403) to prevent workspace existence leakage.
 *
 * <p>The member roster is a read-side projection (per ADR-BE-007) enriched
 * with the safe display fields (email, displayName) from {@code app_user}.
 */
@Service
public class ListWorkspaceMembersService implements ListWorkspaceMembersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListWorkspaceMembersService.class);

    private final WorkspaceAccessChecker accessChecker;
    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceAuditWriter auditWriter;

    public ListWorkspaceMembersService(WorkspaceAccessChecker accessChecker,
                                       WorkspaceMembershipRepository membershipRepository,
                                       WorkspaceAuditWriter auditWriter) {
        this.accessChecker = accessChecker;
        this.membershipRepository = membershipRepository;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceMembersResult listMembers(UUID workspaceId, UUID userId, String requestId) {
        // 1. Verify the caller is an ACTIVE member — throws WorkspaceAccessDeniedException
        //    (mapped to 404 WORKSPACE_NOT_FOUND) for non-members and PENDING/REMOVED.
        accessChecker.requireActiveMember(workspaceId, userId);

        // 2. Project the ACTIVE members with safe display info, ordered by joinedAt ascending.
        List<WorkspaceMemberResult> members = membershipRepository.findActiveMembersByWorkspaceId(workspaceId);

        log.info("Workspace members listed: workspaceId={}, requestedBy={}, memberCount={}",
                workspaceId, userId, members.size());

        // 3. Audit — metadata carries only the count, never emails or PII.
        auditWriter.writeUserEvent("WORKSPACE_MEMBERS_VIEWED", userId, workspaceId,
                "workspace_membership", null, requestId,
                "Workspace members viewed",
                Map.of("memberCount", members.size()));

        return new WorkspaceMembersResult(workspaceId, members);
    }
}
