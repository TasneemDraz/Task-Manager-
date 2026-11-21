package com.tasneem.taskmanager.task;

import java.util.Map;
import java.util.Set;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    /** TODO -> IN_PROGRESS -> DONE, TODO -> DONE directly, and DONE -> IN_PROGRESS (reopen). */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_MOVES = Map.of(
            TODO, Set.of(IN_PROGRESS, DONE),
            IN_PROGRESS, Set.of(DONE),
            DONE, Set.of(IN_PROGRESS));

    public boolean canMoveTo(TaskStatus target) {
        return ALLOWED_MOVES.get(this).contains(target);
    }
}
