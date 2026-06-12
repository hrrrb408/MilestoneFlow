package com.milestoneflow.task.infrastructure.persistence;

import com.milestoneflow.task.application.port.out.TaskRepository;
import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that bridges the application port to Spring Data JPA.
 *
 * <p>Isolates the application layer from Spring Data types, preventing
 * framework leakage across architectural boundaries.
 */
@Component
public class TaskRepositoryAdapter implements TaskRepository {

    private final SpringDataTaskRepository delegate;

    TaskRepositoryAdapter(SpringDataTaskRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Task save(Task task) {
        return delegate.saveAndFlush(task);
    }

    @Override
    public Optional<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
            UUID workspaceId, UUID projectId, UUID milestoneId, UUID taskId) {
        return delegate.findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
                workspaceId, projectId, milestoneId, taskId);
    }

    @Override
    public List<Task> findByWorkspaceIdAndProjectIdAndMilestoneId(
            UUID workspaceId, UUID projectId, UUID milestoneId) {
        return delegate.findAllByWorkspaceIdAndProjectIdAndMilestoneIdOrdered(
                workspaceId, projectId, milestoneId);
    }

    @Override
    public List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatus(
            UUID workspaceId, UUID projectId, UUID milestoneId, TaskStatus status) {
        return delegate.findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusOrdered(
                workspaceId, projectId, milestoneId, status);
    }

    @Override
    public List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndPriority(
            UUID workspaceId, UUID projectId, UUID milestoneId, TaskPriority priority) {
        return delegate.findByWorkspaceIdAndProjectIdAndMilestoneIdAndPriorityOrdered(
                workspaceId, projectId, milestoneId, priority);
    }

    @Override
    public List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusAndPriority(
            UUID workspaceId, UUID projectId, UUID milestoneId,
            TaskStatus status, TaskPriority priority) {
        return delegate.findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusAndPriorityOrdered(
                workspaceId, projectId, milestoneId, status, priority);
    }
}
