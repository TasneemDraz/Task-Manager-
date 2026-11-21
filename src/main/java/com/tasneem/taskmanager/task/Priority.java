package com.tasneem.taskmanager.task;

import java.util.Arrays;

public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    /** Persisted value. Explicit (not the ordinal) so reordering the enum can never corrupt data. */
    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static Priority fromLevel(int level) {
        return Arrays.stream(values())
                .filter(p -> p.level == level)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown priority level: " + level));
    }
}
