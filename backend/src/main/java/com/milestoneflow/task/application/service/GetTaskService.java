package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.task.application.port.in.GetTaskUseCase;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskNotFoundException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for retrieving a single task by composite key.
 *
 * <p>Enforces data isolation by requiring all four keys (workspace, project,
 * milestone, task) to match. Cross-workspace, cross-project, and cross-milestone
 * access all return 404.
 */
@Service
public class GetTaskService implements GetTaskUseCase {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public GetTaskService(TaskRepository taskRepository,
                          ProjectRepository projectRepository,
                          MilestoneRepository milestoneRepository,
                          WorkspaceAccessChecker workspaceAccessChecker) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.workspaceAccessChecker = workspaceAccessChecker;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResult getTask(UUID workspaceId, UUID projectId, UUID milestoneId,
                              UUID taskId, UUID userId, String requestId) {
        // 1. Verify workspace membership
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify milestone belongs to project
        milestoneRepository.findByWorkspaceIdAndProjectIdAndId(workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        // 4. Load task by composite key
        Task task = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                        workspaceId, projectId, milestoneId, taskId)
                .orElseThrow(TaskNotFoundException::new);

        return toResult(task);
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
