package com.tasneem.taskmanager.task.dto;

import com.tasneem.taskmanager.task.Priority;
import com.tasneem.taskmanager.task.TaskStatus;

import java.util.Map;

public record TaskSummaryResponse(
        Map<TaskStatus, Long> byStatus,
        Map<Priority, Long> byPriority,
        long overdue) {
}
