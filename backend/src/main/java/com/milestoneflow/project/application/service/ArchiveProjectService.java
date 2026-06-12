package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.port.in.ArchiveProjectUseCase;
import com.milestoneflow.project.application.port.out.ProjectAuditWriter;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
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
 * Application service for archiving a project.
 *
 * <p>Archive flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Project.archive() validates and applies state change</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event PROJECT_ARCHIVED</li>
 *   <li>Return ProjectResult</li>
 * </ol>
 */
@Service
public class ArchiveProjectService implements ArchiveProjectUseCase {

    private static final Logger log = LoggerFactory.getLogger(ArchiveProjectService.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final ProjectAuditWriter auditWriter;
    private final Clock clock;

    public ArchiveProjectService(ProjectRepository projectRepository,
                                 WorkspaceAccessChecker workspaceAccessChecker,
                                 ProjectAuditWriter auditWriter,
                                 Clock clock) {
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectResult archive(UUID workspaceId, UUID projectId, UUID userId, String requestId) {
        // 1. Verify OWNER role
        workspaceAccessChecker.requireOwner(workspaceId, userId);

        // 2. Load project by composite key
        Project project = projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Build metadata before state change
        String previousStatus = project.getStatus().name();

        // 4. Archive (validates not already ARCHIVED)
        project.archive(userId, clock.instant());

        // 5. Save — use returned managed entity
        Project saved = projectRepository.save(project);

        log.info("Project archived: projectId={}, workspaceId={}", saved.getId(), workspaceId);

        // 6. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", previousStatus);
        metadata.put("newStatus", "ARCHIVED");
        auditWriter.writeUserEvent("PROJECT_ARCHIVED", userId, workspaceId,
                saved.getId(), requestId, "Project archived", metadata);

        // 7. Return result
        return toResult(saved);
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
                project.getArchivedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
