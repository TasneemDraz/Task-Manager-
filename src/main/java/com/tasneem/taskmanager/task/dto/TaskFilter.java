package com.tasneem.taskmanager.task.dto;

import com.tasneem.taskmanager.task.Priority;
import com.tasneem.taskmanager.task.TaskStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Optional query parameters for listing tasks; all can be combined.
 * {@code overdue=true} keeps only overdue tasks; false or absent applies no overdue filter.
 */
public record TaskFilter(
        List<TaskStatus> status,
        List<Priority> priority,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
        Boolean overdue,
        String q) {
}
