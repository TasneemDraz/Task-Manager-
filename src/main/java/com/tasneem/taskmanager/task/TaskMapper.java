package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.task.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final Clock clock;

    public TaskResponse toResponse(Task task) {
        LocalDate today = LocalDate.now(clock);
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.isOverdue(today),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion());
    }
}
