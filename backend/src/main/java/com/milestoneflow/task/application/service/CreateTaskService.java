package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.shared.id.IdGenerator;
import com.milestoneflow.task.application.command.CreateTaskCommand;
import com.milestoneflow.task.application.port.in.CreateTaskUseCase;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for creating a new task within a milestone.
 *
 * <p>Creation flow:
 * <ol>
 *   <li>Verify user has ACTIVE membership in the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Load milestone by composite key (workspaceId + projectId + milestoneId)</li>
 *   <li>Verify milestone is not completed</li>
 *   <li>Resolve priority (defaults to MEDIUM)</li>
 *   <li>Create Task in OPEN status</li>
 *   <li>Save task — use returned managed entity</li>
 *   <li>Write audit event TASK_CREATED</li>
 *   <li>Return TaskResult</li>
 * </ol>
 */
@Service
public class CreateTaskService implements CreateTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final TaskAuditWriter auditWriter;
    private final IdGenerator idGenerator;

    public CreateTaskService(TaskRepository taskRepository,
                             ProjectRepository projectRepository,
                             MilestoneRepository milestoneRepository,
                             WorkspaceAccessChecker workspaceAccessChecker,
                             TaskAuditWriter auditWriter,
                             IdGenerator idGenerator) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public TaskResult create(CreateTaskCommand command, UUID userId, String requestId) {
        // 1. Verify workspace membership (ACTIVE member required)
        workspaceAccessChecker.requireActiveMember(command.workspaceId(), userId);

        // 2. Load project and verify it belongs to workspace
        Project project = projectRepository.findByWorkspaceIdAndId(
                        command.workspaceId(), command.projectId())
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify project is not archived
        if (project.isArchived()) {
            throw new ProjectArchivedException();
        }

        // 4. Load milestone and verify it belongs to project
        Milestone milestone = milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                        command.workspaceId(), command.projectId(), command.milestoneId())
                .orElseThrow(MilestoneNotFoundException::new);

        // 5. Verify milestone is not completed
        if (milestone.isCompleted()) {
            throw new com.milestoneflow.milestone.domain.exception.MilestoneCompletedException();
        }

        // 6. Resolve priority (defaults to MEDIUM)
        TaskPriority priority = resolvePriority(command.priority());

        // 7. Create task
        UUID taskId = idGenerator.nextId();
        Task task = Task.create(
                taskId,
                command.workspaceId(),
                command.projectId(),
                command.milestoneId(),
                command.title(),
                command.description(),
                priority,
                command.dueDate()
        );

        // 8. Save — use returned managed entity for auditing fields
        Task saved = taskRepository.save(task);

        log.info("Task created: taskId={}, milestoneId={}, projectId={}, workspaceId={}, title={}",
                taskId, command.milestoneId(), command.projectId(),
                command.workspaceId(), command.title());

        // 9. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", command.title());
        metadata.put("priority", priority.name());
        auditWriter.writeUserEvent("TASK_CREATED", userId, command.workspaceId(),
                taskId, requestId, "Task created", metadata);

        // 10. Return result from managed entity
        return toResult(saved);
    }

    private static TaskPriority resolvePriority(String priorityStr) {
        if (priorityStr == null || priorityStr.isBlank()) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(priorityStr);
        } catch (IllegalArgumentException e) {
            throw new TaskInvalidPriorityException(priorityStr);
        }
    }

    private static TaskResult toResult(Task task) {
        return new TaskResult(
                task.getId(),
                task.getWorkspaceId(),
                task.getProjectId(),
                task.getMilestoneId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus().name(),
                task.getPriority().name(),
                task.getDueDate(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
