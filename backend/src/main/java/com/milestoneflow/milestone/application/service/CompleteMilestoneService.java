package com.milestoneflow.milestone.application.service;

import com.milestoneflow.milestone.application.port.in.CompleteMilestoneUseCase;
import com.milestoneflow.milestone.application.port.out.MilestoneAuditWriter;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.application.result.MilestoneResult;
import com.milestoneflow.milestone.domain.exception.MilestoneAlreadyCompletedException;
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

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for completing a milestone.
 *
 * <p>Completion flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Load milestone by composite key (workspaceId + projectId + milestoneId)</li>
 *   <li>Transition to COMPLETED status</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event MILESTONE_COMPLETED</li>
 *   <li>Return MilestoneResult</li>
 * </ol>
 */
@Service
public class CompleteMilestoneService implements CompleteMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteMilestoneService.class);

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final MilestoneAuditWriter auditWriter;
    private final Clock clock;

    public CompleteMilestoneService(MilestoneRepository milestoneRepository,
                                    ProjectRepository projectRepository,
                                    WorkspaceAccessChecker workspaceAccessChecker,
                                    MilestoneAuditWriter auditWriter,
                                    Clock clock) {
        this.milestoneRepository = milestoneRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MilestoneResult complete(UUID workspaceId, UUID projectId,
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

        // 4. Transition to COMPLETED
        try {
            milestone.complete(userId, clock.instant());
        } catch (IllegalStateException e) {
            throw new MilestoneAlreadyCompletedException();
        }

        // 5. Save — use returned managed entity
        Milestone saved = milestoneRepository.save(milestone);

        log.info("Milestone completed: milestoneId={}, projectId={}, workspaceId={}",
                saved.getId(), projectId, workspaceId);

        // 6. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", "OPEN");
        metadata.put("newStatus", "COMPLETED");
        auditWriter.writeUserEvent("MILESTONE_COMPLETED", userId, workspaceId,
                saved.getId(), requestId, "Milestone completed", metadata);

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
