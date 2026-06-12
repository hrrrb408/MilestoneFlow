package com.milestoneflow.milestone.application.service;

import com.milestoneflow.milestone.application.command.UpdateMilestoneCommand;
import com.milestoneflow.milestone.application.port.in.UpdateMilestoneUseCase;
import com.milestoneflow.milestone.application.port.out.MilestoneAuditWriter;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.application.result.MilestoneResult;
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
 * Application service for updating basic milestone information.
 *
 * <p>V0.1 requires OWNER role for updates. Archived projects cannot have milestones updated.
 *
 * <p>Update flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Load milestone by composite key (workspaceId + projectId + milestoneId)</li>
 *   <li>Apply updates</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event MILESTONE_UPDATED</li>
 *   <li>Return MilestoneResult</li>
 * </ol>
 */
@Service
public class UpdateMilestoneService implements UpdateMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateMilestoneService.class);

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final MilestoneAuditWriter auditWriter;

    public UpdateMilestoneService(MilestoneRepository milestoneRepository,
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
    public MilestoneResult update(UpdateMilestoneCommand command, UUID userId, String requestId) {
        // 1. Verify OWNER role
        workspaceAccessChecker.requireOwner(command.workspaceId(), userId);

        // 2. Load project and verify it belongs to workspace and is not archived
        var project = projectRepository.findByWorkspaceIdAndId(command.workspaceId(), command.projectId())
                .orElseThrow(ProjectNotFoundException::new);

        if (project.isArchived()) {
            throw new ProjectArchivedException();
        }

        // 4. Load milestone by composite key
        Milestone milestone = milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                        command.workspaceId(), command.projectId(), command.milestoneId())
                .orElseThrow(MilestoneNotFoundException::new);

        // 5. Build change metadata
        Map<String, Object> changes = buildChangeMetadata(milestone, command);

        // 6. Apply updates
        milestone.updateBasicInfo(command.title(), command.description(), command.dueDate());

        // 7. Save — use returned managed entity
        Milestone saved = milestoneRepository.save(milestone);

        log.info("Milestone updated: milestoneId={}, projectId={}, workspaceId={}",
                saved.getId(), command.projectId(), command.workspaceId());

        // 8. Audit
        auditWriter.writeUserEvent("MILESTONE_UPDATED", userId, command.workspaceId(),
                saved.getId(), requestId, "Milestone updated", changes);

        // 9. Return result
        return toResult(saved);
    }

    private static Map<String, Object> buildChangeMetadata(Milestone milestone, UpdateMilestoneCommand command) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (command.title() != null && !command.title().equals(milestone.getTitle())) {
            changes.put("titleChanged", true);
        }
        if (command.description() != null) {
            boolean descChanged = (milestone.getDescription() == null && !command.description().isEmpty())
                    || (milestone.getDescription() != null && !milestone.getDescription().equals(command.description()));
            if (descChanged) {
                changes.put("descriptionChanged", true);
            }
        }
        if (command.dueDate() != null && !command.dueDate().equals(milestone.getDueDate())) {
            changes.put("dueDateChanged", true);
        }
        return changes;
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
                milestone.getCreatedAt(),
                milestone.getUpdatedAt()
        );
    }
}
