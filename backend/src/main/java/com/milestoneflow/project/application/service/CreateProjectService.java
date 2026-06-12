package com.milestoneflow.project.application.service;

import com.milestoneflow.project.application.command.CreateProjectCommand;
import com.milestoneflow.project.application.port.in.CreateProjectUseCase;
import com.milestoneflow.project.application.port.out.ProjectAuditWriter;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.application.result.ProjectResult;
import com.milestoneflow.project.domain.exception.ProjectInvalidDateRangeException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for creating a new project within a workspace.
 *
 * <p>Creation flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Validate request (name, date range)</li>
 *   <li>Create Project in ACTIVE status</li>
 *   <li>Save project — use returned managed entity</li>
 *   <li>Write audit event PROJECT_CREATED</li>
 *   <li>Return ProjectResult</li>
 * </ol>
 */
@Service
public class CreateProjectService implements CreateProjectUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateProjectService.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final ProjectAuditWriter auditWriter;
    private final IdGenerator idGenerator;

    public CreateProjectService(ProjectRepository projectRepository,
                                WorkspaceAccessChecker workspaceAccessChecker,
                                ProjectAuditWriter auditWriter,
                                IdGenerator idGenerator) {
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public ProjectResult create(CreateProjectCommand command, UUID userId, String requestId) {
        // 1. Verify workspace membership (ACTIVE member required)
        workspaceAccessChecker.requireActiveMember(command.workspaceId(), userId);

        // 2. Validate date range
        validateDateRange(command.startDate(), command.targetDate());

        // 3. Create project
        UUID projectId = idGenerator.nextId();
        Project project = Project.create(
                projectId,
                command.workspaceId(),
                command.name(),
                command.description(),
                command.startDate(),
                command.targetDate()
        );

        // 4. Save — use returned managed entity for auditing fields
        Project saved = projectRepository.save(project);

        log.info("Project created: projectId={}, workspaceId={}, name={}",
                projectId, command.workspaceId(), command.name());

        // 5. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", command.name());
        auditWriter.writeUserEvent("PROJECT_CREATED", userId, command.workspaceId(),
                projectId, requestId, "Project created", metadata);

        // 6. Return result from managed entity
        return toResult(saved);
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate targetDate) {
        if (startDate != null && targetDate != null && startDate.isAfter(targetDate)) {
            throw new ProjectInvalidDateRangeException();
        }
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
