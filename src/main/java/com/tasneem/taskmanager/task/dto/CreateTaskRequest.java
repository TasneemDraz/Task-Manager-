package com.tasneem.taskmanager.task.dto;

import com.tasneem.taskmanager.task.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(min = 3, max = 120, message = "must be between 3 and 120 characters")
        String title,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        /** Optional, defaults to MEDIUM. */
        Priority priority,

        @FutureOrPresent
        LocalDate dueDate) {
}
