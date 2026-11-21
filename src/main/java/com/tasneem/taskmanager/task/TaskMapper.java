package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.task.dto.TaskResponse;
import com.tasneem.taskmanager.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final Clock clock;

    /** For a user's own tasks: the owner is the caller, so it is left out. */
    public TaskResponse toResponse(Task task) {
        return map(task, null);
    }

    /** For the admin view of all tasks: includes who owns each one. */
    public TaskResponse toAdminResponse(Task task) {
        User owner = task.getOwner();
        return map(task, new TaskResponse.Owner(owner.getId(), owner.getEmail()));
    }

    private TaskResponse map(Task task, TaskResponse.Owner owner) {
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
                task.getVersion(),
                owner);
    }
}
