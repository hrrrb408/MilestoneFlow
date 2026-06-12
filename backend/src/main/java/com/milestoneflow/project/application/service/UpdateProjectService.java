package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.command.UpdateProjectCommand;
import com.milestoneflow.project.application.port.in.UpdateProjectUseCase;
import com.milestoneflow.project.application.port.out.ProjectAuditWriter;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectInvalidDateRangeException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for updating basic project information.
 *
 * <p>V0.1 requires OWNER role for updates. Archived projects cannot be updated.
 *
 * <p>Update flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Validate date range if both dates are provided</li>
 *   <li>Apply updates</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event PROJECT_UPDATED</li>
 *   <li>Return ProjectResult</li>
 * </ol>
 */
@Service
public class UpdateProjectService implements UpdateProjectUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateProjectService.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final ProjectAuditWriter auditWriter;

    public UpdateProjectService(ProjectRepository projectRepository,
                                WorkspaceAccessChecker workspaceAccessChecker,
                                ProjectAuditWriter auditWriter) {
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public ProjectResult update(UpdateProjectCommand command, UUID userId, String requestId) {
        // 1. Verify OWNER role
        workspaceAccessChecker.requireOwner(command.workspaceId(), userId);

        // 2. Load project by composite key
        Project project = projectRepository.findByWorkspaceIdAndId(command.workspaceId(), command.projectId())
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify not archived
        if (project.getStatus() == com.milestoneflow.project.domain.type.ProjectStatus.ARCHIVED) {
            throw new ProjectArchivedException();
        }

        // 4. Resolve effective dates for range validation
        LocalDate effectiveStart = command.startDate() != null ? command.startDate() : project.getStartDate();
        LocalDate effectiveTarget = command.targetDate() != null ? command.targetDate() : project.getTargetDate();
        if (effectiveStart != null && effectiveTarget != null && effectiveStart.isAfter(effectiveTarget)) {
            throw new ProjectInvalidDateRangeException();
        }

        // 5. Build change metadata
        Map<String, Object> changes = buildChangeMetadata(project, command);

        // 6. Apply updates
        project.updateBasicInfo(command.name(), command.description(),
                command.startDate(), command.targetDate());

        // 7. Save — use returned managed entity
        Project saved = projectRepository.save(project);

        log.info("Project updated: projectId={}, workspaceId={}", saved.getId(), command.workspaceId());

        // 8. Audit
        auditWriter.writeUserEvent("PROJECT_UPDATED", userId, command.workspaceId(),
                saved.getId(), requestId, "Project updated", changes);

        // 9. Return result
        return toResult(saved);
    }

    private static Map<String, Object> buildChangeMetadata(Project project, UpdateProjectCommand command) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (command.name() != null && !command.name().equals(project.getName())) {
            changes.put("nameChanged", true);
        }
        if (command.description() != null) {
            boolean descChanged = (project.getDescription() == null && !command.description().isEmpty())
                    || (project.getDescription() != null && !project.getDescription().equals(command.description()));
            if (descChanged) {
                changes.put("descriptionChanged", true);
            }
        }
        if (command.startDate() != null && !command.startDate().equals(project.getStartDate())) {
            changes.put("startDateChanged", true);
        }
        if (command.targetDate() != null && !command.targetDate().equals(project.getTargetDate())) {
            changes.put("targetDateChanged", true);
        }
        return changes;
    }

    private static ProjectResult toResult(Project project) {
        return new ProjectResult(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getStartDate(),
                project.getTargetDate(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
