package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.common.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Parses the {@code sort} query parameter, e.g. "dueDate,asc". Only whitelisted fields are allowed. */
public final class TaskSorting {

    public static final String DEFAULT = "createdAt,desc";

    private static final Set<String> SORTABLE_FIELDS = Set.of("dueDate", "priority", "createdAt", "title");

    private TaskSorting() {
    }

    public static Sort parse(String sortParam) {
        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field) || parts.length > 2) {
            throw invalid();
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim()).orElseThrow(TaskSorting::invalid);
        }
        // id as a tie-breaker keeps paging stable when many rows share the same value
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static BadRequestException invalid() {
        return new BadRequestException("sort",
                "must be one of dueDate, priority, createdAt, title, optionally followed by ,asc or ,desc");
    }
}
