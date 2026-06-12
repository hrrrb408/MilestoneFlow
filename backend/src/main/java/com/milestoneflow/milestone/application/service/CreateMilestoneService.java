package com.milestoneflow.milestone.application.service;

import com.milestoneflow.milestone.application.command.CreateMilestoneCommand;
import com.milestoneflow.milestone.application.port.in.CreateMilestoneUseCase;
import com.milestoneflow.milestone.application.port.out.MilestoneAuditWriter;
import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.application.result.MilestoneResult;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.project.application.port.out.ProjectRepository;
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
 * Application service for creating a new milestone within a project.
 *
 * <p>Creation flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Create Milestone in OPEN status</li>
 *   <li>Save milestone — use returned managed entity</li>
 *   <li>Write audit event MILESTONE_CREATED</li>
 *   <li>Return MilestoneResult</li>
 * </ol>
 */
@Service
public class CreateMilestoneService implements CreateMilestoneUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateMilestoneService.class);

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final MilestoneAuditWriter auditWriter;
    private final IdGenerator idGenerator;

    public CreateMilestoneService(MilestoneRepository milestoneRepository,
                                  ProjectRepository projectRepository,
                                  WorkspaceAccessChecker workspaceAccessChecker,
                                  MilestoneAuditWriter auditWriter,
                                  IdGenerator idGenerator) {
        this.milestoneRepository = milestoneRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public MilestoneResult create(CreateMilestoneCommand command, UUID userId, String requestId) {
        // 1. Verify workspace membership (ACTIVE member required)
        workspaceAccessChecker.requireActiveMember(command.workspaceId(), userId);

        // 2. Load project and verify it belongs to workspace
        Project project = projectRepository.findByWorkspaceIdAndId(command.workspaceId(), command.projectId())
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify project is not archived
        if (project.isArchived()) {
            throw new ProjectArchivedException();
        }

        // 4. Create milestone
        UUID milestoneId = idGenerator.nextId();
        Milestone milestone = Milestone.create(
                milestoneId,
                command.workspaceId(),
                command.projectId(),
                command.title(),
                command.description(),
                command.dueDate()
        );

        // 5. Save — use returned managed entity for auditing fields
        Milestone saved = milestoneRepository.save(milestone);

        log.info("Milestone created: milestoneId={}, projectId={}, workspaceId={}, title={}",
                milestoneId, command.projectId(), command.workspaceId(), command.title());

        // 6. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", command.title());
        auditWriter.writeUserEvent("MILESTONE_CREATED", userId, command.workspaceId(),
                milestoneId, requestId, "Milestone created", metadata);

        // 7. Return result from managed entity
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
                milestone.getCreatedAt(),
                milestone.getUpdatedAt()
        );
    }
}
