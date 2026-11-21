package com.tasneem.taskmanager.task.dto;

import com.tasneem.taskmanager.task.Priority;
import com.tasneem.taskmanager.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        boolean overdue,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
