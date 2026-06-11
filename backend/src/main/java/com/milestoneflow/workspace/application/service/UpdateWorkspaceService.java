package com.milestoneflow.workspace.application.service;

import com.milestoneflow.workspace.application.command.UpdateWorkspaceCommand;
import com.milestoneflow.workspace.application.port.in.UpdateWorkspaceUseCase;
import com.milestoneflow.workspace.application.port.out.WorkspaceAuditWriter;
import com.milestoneflow.workspace.application.port.out.WorkspaceRepository;
import com.milestoneflow.workspace.application.result.WorkspaceResult;
import com.milestoneflow.workspace.domain.model.Workspace;
import com.milestoneflow.workspace.domain.model.WorkspaceMembership;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for updating basic workspace information.
 *
 * <p>Requires the caller to be the OWNER of the workspace.
 * V0.1 allows updating name, timezone, and defaultCurrency.
 * V0.1 does not allow slug changes.
 */
@Service
public class UpdateWorkspaceService implements UpdateWorkspaceUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateWorkspaceService.class);

    private final WorkspaceAccessChecker accessChecker;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuditWriter auditWriter;

    public UpdateWorkspaceService(WorkspaceAccessChecker accessChecker,
                                  WorkspaceRepository workspaceRepository,
                                  WorkspaceAuditWriter auditWriter) {
        this.accessChecker = accessChecker;
        this.workspaceRepository = workspaceRepository;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public WorkspaceResult update(UpdateWorkspaceCommand command, UUID userId, String requestId) {
        // 1. Verify OWNER membership
        WorkspaceMembership membership = accessChecker.requireOwner(command.workspaceId(), userId);

        // 2. Load workspace
        Workspace workspace = accessChecker.findWorkspaceOrThrow(command.workspaceId());

        // 3. Build audit metadata for changed fields
        Map<String, Object> auditMetadata = new HashMap<>();
        if (command.name() != null && !command.name().equals(workspace.getName())) {
            auditMetadata.put("nameChanged", true);
        }
        if (command.timezone() != null && !command.timezone().equals(workspace.getTimezone())) {
            auditMetadata.put("timezoneChanged", true);
        }
        if (command.defaultCurrency() != null && !command.defaultCurrency().equals(workspace.getDefaultCurrency())) {
            auditMetadata.put("defaultCurrencyChanged", true);
        }

        // 4. Apply updates
        workspace.updateBasicInfo(command.name(), command.timezone(), command.defaultCurrency());

        // 5. Save
        workspaceRepository.save(workspace);

        log.info("Workspace updated: workspaceId={}, updatedBy={}", command.workspaceId(), userId);

        // 6. Audit
        auditWriter.writeUserEvent("WORKSPACE_UPDATED", userId, workspace.getId(),
                "workspace", workspace.getId(), requestId,
                "Workspace updated", auditMetadata.isEmpty() ? null : auditMetadata);

        return new WorkspaceResult(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getStatus().name(),
                workspace.getTimezone(),
                workspace.getDefaultCurrency(),
                membership.getRole().name(),
                workspace.getCreatedAt()
        );
    }
}
