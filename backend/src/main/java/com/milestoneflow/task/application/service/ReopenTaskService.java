package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneCompletedException;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.milestone.domain.model.Milestone;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectArchivedException;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.project.domain.model.Project;
import com.milestoneflow.task.application.port.in.ReopenTaskUseCase;
import com.milestoneflow.task.application.port.out.TaskAuditWriter;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskNotCompletedException;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for reopening a completed task.
 *
 * <p>Reopen flow:
 * <ol>
 *   <li>Verify user is OWNER of the workspace</li>
 *   <li>Load project by composite key (workspaceId + projectId)</li>
 *   <li>Verify project is not archived</li>
 *   <li>Load milestone by composite key (workspaceId + projectId + milestoneId)</li>
 *   <li>Verify milestone is not completed</li>
 *   <li>Load task by composite key (workspaceId + projectId + milestoneId + taskId)</li>
 *   <li>Transition from COMPLETED back to OPEN</li>
 *   <li>Save — use returned managed entity</li>
 *   <li>Write audit event TASK_REOPENED</li>
 *   <li>Return TaskResult</li>
 * </ol>
 */
@Service
public class ReopenTaskService implements ReopenTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReopenTaskService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;
    private final TaskAuditWriter auditWriter;

    public ReopenTaskService(TaskRepository taskRepository,
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
    public TaskResult reopen(UUID workspaceId, UUID projectId, UUID milestoneId,
                             UUID taskId, UUID userId, String requestId) {
        // 1. Verify OWNER role
        workspaceAccessChecker.requireOwner(workspaceId, userId);

        // 2. Load project and verify it belongs to workspace and is not archived
        Project project = projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        if (project.isArchived()) {
            throw new ProjectArchivedException();
        }

        // 3. Load milestone and verify it belongs to project and is not completed
        Milestone milestone = milestoneRepository.findByWorkspaceIdAndProjectIdAndId(
                        workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        if (milestone.isCompleted()) {
            throw new MilestoneCompletedException();
        }

        // 4. Load task by composite key
        Task task = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                        workspaceId, projectId, milestoneId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        // 5. Transition from COMPLETED to OPEN
        try {
            task.reopen();
        } catch (IllegalStateException e) {
            throw new TaskNotCompletedException();
        }

        // 6. Save — use returned managed entity
        Task saved = taskRepository.save(task);

        log.info("Task reopened: taskId={}, milestoneId={}, projectId={}, workspaceId={}",
                saved.getId(), milestoneId, projectId, workspaceId);

        // 7. Audit
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousStatus", "COMPLETED");
        metadata.put("newStatus", "OPEN");
        auditWriter.writeUserEvent("TASK_REOPENED", userId, workspaceId,
                saved.getId(), requestId, "Task reopened", metadata);

        // 8. Return result
        return toResult(saved);
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
