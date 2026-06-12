package com.milestoneflow.task.application.port.out;

import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
            UUID workspaceId, UUID projectId, UUID milestoneId, UUID taskId);

    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneId(
            UUID workspaceId, UUID projectId, UUID milestoneId);

    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatus(
            UUID workspaceId, UUID projectId, UUID milestoneId, TaskStatus status);

    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndPriority(
            UUID workspaceId, UUID projectId, UUID milestoneId, TaskPriority priority);

    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusAndPriority(
            UUID workspaceId, UUID projectId, UUID milestoneId,
            TaskStatus status, TaskPriority priority);
}
