package com.tasneem.taskmanager.task.dto;

import com.tasneem.taskmanager.task.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Full replacement of the editable fields. dueDate has no @FutureOrPresent here:
 * an existing (now past) due date may be sent back unchanged, so the service only
 * rejects a past date when the due date actually changes.
 */
public record UpdateTaskRequest(
        @NotBlank @Size(min = 3, max = 120)
        String title,

        @Size(max = 2000)
        String description,

        @NotNull
        Priority priority,

        LocalDate dueDate,

        /** The version the client last read, for optimistic locking. */
        @NotNull
        Long version) {
}
