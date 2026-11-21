package com.tasneem.taskmanager.task.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        Long version,

        /** Only filled in for the admin view; omitted from the JSON for a user's own tasks. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Owner owner) {

    public record Owner(Long id, String email) {
    }
}
