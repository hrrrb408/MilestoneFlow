package com.milestoneflow.task.domain.type;

/**
 * Task status enum.
 *
 * <p>B5-001 defines two statuses:
 * <ul>
 *   <li>{@code OPEN} — initial status for new tasks</li>
 *   <li>{@code COMPLETED} — reserved for B5-002 (task completion workflow)</li>
 * </ul>
 */
public enum TaskStatus {
    OPEN,
    COMPLETED
}
