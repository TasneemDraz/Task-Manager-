package com.tasneem.taskmanager.task.dto;

import com.tasneem.taskmanager.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull
        TaskStatus status,

        /** The version the client last read, for optimistic locking. */
        @NotNull
        Long version) {
}
