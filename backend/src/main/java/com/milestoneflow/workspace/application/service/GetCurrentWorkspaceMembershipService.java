package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.port.in.GetCurrentWorkspaceMembershipUseCase;
import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.result.CurrentWorkspaceMembershipResult;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for retrieving the current user's membership in a workspace.
 *
 * <p>Requires the caller to have an ACTIVE membership in the workspace.
 * Non-members receive 404 (not 403) to prevent workspace existence leakage.
 *
 * <p>Returns only the caller's own role and status — no email/displayName and
 * no sensitive fields.
 */
@Service
public class GetCurrentWorkspaceMembershipService implements GetCurrentWorkspaceMembershipUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetCurrentWorkspaceMembershipService.class);

    private final WorkspaceAccessChecker accessChecker;
    private final WorkspaceAuditWriter auditWriter;

    public GetCurrentWorkspaceMembershipService(WorkspaceAccessChecker accessChecker,
                                                WorkspaceAuditWriter auditWriter) {
        this.accessChecker = accessChecker;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentWorkspaceMembershipResult getCurrentMembership(UUID workspaceId, UUID userId, String requestId) {
        // 1. Verify the caller is an ACTIVE member — throws WorkspaceAccessDeniedException
        //    (mapped to 404 WORKSPACE_NOT_FOUND) for non-members and PENDING/REMOVED.
        WorkspaceMembership membership = accessChecker.requireActiveMember(workspaceId, userId);

        log.info("Workspace membership self-viewed: workspaceId={}, userId={}", workspaceId, userId);

        // 2. Audit — no metadata; the actor and workspace context are sufficient.
        auditWriter.writeUserEvent("WORKSPACE_MEMBER_SELF_VIEWED", userId, workspaceId,
                "workspace_membership", null, requestId,
                "Workspace membership self-viewed", null);

        return new CurrentWorkspaceMembershipResult(
                workspaceId,
                userId,
                membership.getRole().name(),
                membership.getStatus().name(),
                membership.getJoinedAt()
        );
    }
}
