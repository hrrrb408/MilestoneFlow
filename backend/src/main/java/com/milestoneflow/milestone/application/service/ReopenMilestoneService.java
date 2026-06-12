package com.milestoneflow.milestone.application.service;

import com.milestoneflow.milestone.application.port.in.ReopenMilestoneUseCase;
import com.milestoneflow.milestone.application.port.out.MilestoneAuditWriter;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.application.result.MilestoneResult;
import com.milestoneflow.milestone.domain.exception.MilestoneNotCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for reopening a completed milestone.
 *
 * <p>Reopen flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Load milestone by composite key (workspaceId + projectId + milestoneId)</li>
 *   <li>Transition from COMPLETED back to OPEN</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event MILESTONE_REOPENED</li>
 *   <li>Return MilestoneResult</li>
 * </ol>
 */
@Service
public class ReopenMilestoneService implements ReopenMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReopenMilestoneService.class);

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final MilestoneAuditWriter auditWriter;

    public ReopenMilestoneService(MilestoneRepository milestoneRepository,
                                  ProjectRepository projectRepository,
                                  WorkspaceAccessChecker workspaceAccessChecker,
                                  MilestoneAuditWriter auditWriter) {
        this.milestoneRepository = milestoneRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public MilestoneResult reopen(UUID workspaceId, UUID projectId,
                                  UUID milestoneId, UUID userId, String requestId) {
        // 1. Verify OWNER role
        workspaceAccessChecker.requireOwner(workspaceId, userId);

        // 2. Load project and verify it belongs to workspace and is not archived
        var project = projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        if (project.isArchived()) {
            throw new ProjectArchivedException();
        }

        // 3. Load milestone by composite key
        Milestone milestone = milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                        workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        // 4. Transition from COMPLETED to OPEN
        try {
            milestone.reopen();
        } catch (IllegalStateException e) {
            throw new MilestoneNotCompletedException();
        }

        // 5. Save — use returned managed entity
        Milestone saved = milestoneRepository.save(milestone);

        log.info("Milestone reopened: milestoneId={}, projectId={}, workspaceId={}",
                saved.getId(), projectId, workspaceId);

        // 6. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", "COMPLETED");
        metadata.put("newStatus", "OPEN");
        auditWriter.writeUserEvent("MILESTONE_REOPENED", userId, workspaceId,
                saved.getId(), requestId, "Milestone reopened", metadata);

        // 7. Return result
        return toResult(saved);
    }

    private static MilestoneResult toResult(Milestone milestone) {
        return new MilestoneResult(
                milestone.getId(),
                milestone.getWorkspaceId(),
                milestone.getProjectId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getStatus().name(),
                milestone.getDueDate(),
                milestone.getCompletedAt(),
                milestone.getCreatedAt(),
                milestone.getUpdatedAt()
        );
    }
}
