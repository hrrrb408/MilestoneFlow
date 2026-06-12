package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.task.application.command.UpdateTaskCommand;
import com.milestoneflow.task.application.port.in.UpdateTaskUseCase;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskCompletedException;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
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
 * Application service for updating basic task information.
 *
 * <p>V0.1 requires OWNER role for updates. Archived projects and completed
 * milestones cannot have tasks updated.
 *
 * <p>Update flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Load milestone by composite key (workspaceId + projectId + milestoneId)</li>
 *   <li>Verify milestone is not completed</li>
 *   <li>Load task by composite key (workspaceId + projectId + milestoneId + taskId)</li>
 *   <li>Build change metadata</li>
 *   <li>Apply updates</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event TASK_UPDATED</li>
 *   <li>Return TaskResult</li>
 * </ol>
 */
@Service
public class UpdateTaskService implements UpdateTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateTaskService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final TaskAuditWriter auditWriter;

    public UpdateTaskService(TaskRepository taskRepository,
                             ProjectRepository projectRepository,
                             MilestoneRepository milestoneRepository,
                             WorkspaceAccessChecker workspaceAccessChecker,
                             TaskAuditWriter auditWriter) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public TaskResult update(UpdateTaskCommand command, UUID userId, String requestId) {
        // 1. Verify OWNER role
        workspaceAccessChecker.requireOwner(command.workspaceId(), userId);

        // 2. Load project and verify it belongs to workspace and is not archived
        Project project = projectRepository.findByWorkspaceIdAndId(
                        command.workspaceId(), command.projectId())
                .orElseThrow(ProjectNotFoundException::new);

        if (project.isArchived()) {
            throw new ProjectArchivedException();
        }

        // 3. Load milestone and verify it belongs to project
        Milestone milestone = milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                        command.workspaceId(), command.projectId(), command.milestoneId())
                .orElseThrow(MilestoneNotFoundException::new);

        // 4. Reject update if milestone is COMPLETED
        if (milestone.isCompleted()) {
            throw new MilestoneCompletedException();
        }

        // 5. Load task by composite key
        Task task = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                        command.workspaceId(), command.projectId(),
                        command.milestoneId(), command.taskId())
                .orElseThrow(TaskNotFoundException::new);

        // 6. Reject update if task is COMPLETED
        if (task.isCompleted()) {
            throw new TaskCompletedException();
        }

        // 7. Resolve priority
        TaskPriority priority = resolvePriority(command.priority());

        // 8. Build change metadata
        Map<String, Object> changes = buildChangeMetadata(task, command, priority);

        // 9. Apply updates
        task.updateBasicInfo(command.title(), command.description(), priority, command.dueDate());

        // 10. Save — use returned managed entity
        Task saved = taskRepository.save(task);

        log.info("Task updated: taskId={}, milestoneId={}, projectId={}, workspaceId={}",
                saved.getId(), command.milestoneId(), command.projectId(), command.workspaceId());

        // 11. Audit
        auditWriter.writeUserEvent("TASK_UPDATED", userId, command.workspaceId(),
                saved.getId(), requestId, "Task updated", changes);

        // 12. Return result
        return toResult(saved);
    }

    private static TaskPriority resolvePriority(String priorityStr) {
        if (priorityStr == null || priorityStr.isBlank()) {
            return null;
        }
        try {
            return TaskPriority.valueOf(priorityStr);
        } catch (IllegalArgumentException e) {
            throw new TaskInvalidPriorityException(priorityStr);
        }
    }

    private static Map<String, Object> buildChangeMetadata(Task task,
                                                           UpdateTaskCommand command,
                                                           TaskPriority resolvedPriority) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (command.title() != null && !command.title().equals(task.getTitle())) {
            changes.put("titleChanged", true);
        }
        if (command.description() != null) {
            boolean descChanged = (task.getDescription() == null && !command.description().isEmpty())
                    || (task.getDescription() != null && !task.getDescription().equals(command.description()));
            if (descChanged) {
                changes.put("descriptionChanged", true);
            }
        }
        if (resolvedPriority != null && resolvedPriority != task.getPriority()) {
            changes.put("priorityChanged", true);
        }
        if (command.dueDate() != null && !command.dueDate().equals(task.getDueDate())) {
            changes.put("dueDateChanged", true);
        }
        return changes;
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
