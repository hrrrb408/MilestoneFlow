package com.milestoneflow.task.application.service;

import com.milestoneflow.milestone.application.port.out.MilestoneRepository;
import com.milestoneflow.milestone.domain.exception.MilestoneNotFoundException;
import com.milestoneflow.project.application.port.out.ProjectRepository;
import com.milestoneflow.project.domain.exception.ProjectNotFoundException;
import com.milestoneflow.task.application.port.in.ListTasksUseCase;
import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.application.result.TaskResult;
import com.milestoneflow.task.domain.exception.TaskInvalidPriorityException;
import com.milestoneflow.task.domain.exception.TaskInvalidStatusException;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;
import com.milestoneflow.workspace.application.service.WorkspaceAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for listing tasks within a milestone.
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>Default (no filters): returns all tasks ordered by status, due date, priority, creation time.</li>
 *   <li>{@code status=OPEN}: returns only OPEN tasks.</li>
 *   <li>{@code status=COMPLETED}: returns only COMPLETED tasks.</li>
 *   <li>{@code priority=LOW/MEDIUM/HIGH}: returns only matching priority tasks.</li>
 *   <li>Both status and priority can be combined.</li>
 * </ul>
 *
 * <p>ARCHIVED projects and COMPLETED milestones can still be queried for tasks (read-only access).
 */
@Service
public class ListTasksService implements ListTasksUseCase {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final MilestoneRepository milestoneRepository;
    private final WorkspaceAccessChecker workspaceAccessChecker;

    public ListTasksService(TaskRepository taskRepository,
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
    public List<TaskResult> listTasks(UUID workspaceId, UUID projectId, UUID milestoneId,
                                      UUID userId, String requestId,
                                      String status, String priority) {
        // 1. Verify workspace membership
        workspaceAccessChecker.requireActiveMember(workspaceId, userId);

        // 2. Verify project belongs to workspace
        projectRepository.findByWorkspaceIdAndId(workspaceId, projectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 3. Verify milestone belongs to project
        milestoneRepository.findByWorkspaceIdAndProjectIdAndId(workspaceId, projectId, milestoneId)
                .orElseThrow(MilestoneNotFoundException::new);

        // 4. Resolve filters
        TaskStatus statusFilter = resolveStatus(status);
        TaskPriority priorityFilter = resolvePriority(priority);

        // 5. Query tasks with filters
        List<Task> tasks;
        if (statusFilter != null && priorityFilter != null) {
            tasks = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusAndPriority(
                    workspaceId, projectId, milestoneId, statusFilter, priorityFilter);
        } else if (statusFilter != null) {
            tasks = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatus(
                    workspaceId, projectId, milestoneId, statusFilter);
        } else if (priorityFilter != null) {
            tasks = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneIdAndPriority(
                    workspaceId, projectId, milestoneId, priorityFilter);
        } else {
            tasks = taskRepository.findByWorkspaceIdAndProjectIdAndMilestoneId(
                    workspaceId, projectId, milestoneId);
        }

        return tasks.stream()
                .map(ListTasksService::toResult)
                .toList();
    }

    private static TaskStatus resolveStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new TaskInvalidStatusException(status);
        }
    }

    private static TaskPriority resolvePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return TaskPriority.valueOf(priority);
        } catch (IllegalArgumentException e) {
            throw new TaskInvalidPriorityException(priority);
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
