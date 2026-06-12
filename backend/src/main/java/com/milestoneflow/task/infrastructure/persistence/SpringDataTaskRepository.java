package com.milestoneflow.task.infrastructure.persistence;

import com.milestoneflow.task.domain.model.Task;
import com.milestoneflow.task.domain.type.TaskPriority;
import com.milestoneflow.task.domain.type.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Task entities.
 *
 * <p>Package-private per ARCH-010. Accessed exclusively through
 * {@link TaskRepositoryAdapter}.
 */
interface SpringDataTaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndId(
            UUID workspaceId, UUID projectId, UUID milestoneId, UUID id);

    @Query("SELECT t FROM Task t " +
            "WHERE t.workspaceId = :workspaceId " +
            "AND t.projectId = :projectId " +
            "AND t.milestoneId = :milestoneId " +
            "ORDER BY " +
            "CASE WHEN t.status = com.milestoneflow.task.domain.type.TaskStatus.OPEN THEN 0 ELSE 1 END, " +
            "CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, " +
            "t.dueDate ASC, " +
            "CASE WHEN t.priority = com.milestoneflow.task.domain.type.TaskPriority.HIGH THEN 0 " +
            "     WHEN t.priority = com.milestoneflow.task.domain.type.TaskPriority.MEDIUM THEN 1 " +
            "     ELSE 2 END, " +
            "t.createdAt ASC")
    List<Task> findAllByWorkspaceIdAndProjectIdAndMilestoneIdOrdered(
            UUID workspaceId, UUID projectId, UUID milestoneId);

    @Query("SELECT t FROM Task t " +
            "WHERE t.workspaceId = :workspaceId " +
            "AND t.projectId = :projectId " +
            "AND t.milestoneId = :milestoneId " +
            "AND t.status = :status " +
            "ORDER BY " +
            "CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, " +
            "t.dueDate ASC, " +
            "CASE WHEN t.priority = com.milestoneflow.task.domain.type.TaskPriority.HIGH THEN 0 " +
            "     WHEN t.priority = com.milestoneflow.task.domain.type.TaskPriority.MEDIUM THEN 1 " +
            "     ELSE 2 END, " +
            "t.createdAt ASC")
    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusOrdered(
            UUID workspaceId, UUID projectId, UUID milestoneId, TaskStatus status);

    @Query("SELECT t FROM Task t " +
            "WHERE t.workspaceId = :workspaceId " +
            "AND t.projectId = :projectId " +
            "AND t.milestoneId = :milestoneId " +
            "AND t.priority = :priority " +
            "ORDER BY " +
            "CASE WHEN t.status = com.milestoneflow.task.domain.type.TaskStatus.OPEN THEN 0 ELSE 1 END, " +
            "CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, " +
            "t.dueDate ASC, " +
            "t.createdAt ASC")
    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndPriorityOrdered(
            UUID workspaceId, UUID projectId, UUID milestoneId, TaskPriority priority);

    @Query("SELECT t FROM Task t " +
            "WHERE t.workspaceId = :workspaceId " +
            "AND t.projectId = :projectId " +
            "AND t.milestoneId = :milestoneId " +
            "AND t.status = :status " +
            "AND t.priority = :priority " +
            "ORDER BY " +
            "CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, " +
            "t.dueDate ASC, " +
            "t.createdAt ASC")
    List<Task> findByWorkspaceIdAndProjectIdAndMilestoneIdAndStatusAndPriorityOrdered(
            UUID workspaceId, UUID projectId, UUID milestoneId,
            TaskStatus status, TaskPriority priority);
}
