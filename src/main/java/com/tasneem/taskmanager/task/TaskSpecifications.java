package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.task.dto.TaskFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the WHERE clause for task listings from the optional filters. */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> matching(TaskFilter filter, LocalDate today) {
        List<Specification<Task>> specs = new ArrayList<>();
        if (filter.status() != null && !filter.status().isEmpty()) {
            specs.add(statusIn(filter.status()));
        }
        if (filter.priority() != null && !filter.priority().isEmpty()) {
            specs.add(priorityIn(filter.priority()));
        }
        if (filter.dueFrom() != null) {
            specs.add(dueOnOrAfter(filter.dueFrom()));
        }
        if (filter.dueTo() != null) {
            specs.add(dueOnOrBefore(filter.dueTo()));
        }
        if (Boolean.TRUE.equals(filter.overdue())) {
            specs.add(overdue(today));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            specs.add(titleOrDescriptionContains(filter.q()));
        }
        return Specification.allOf(specs);
    }

    public static Specification<Task> ownedBy(Long ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    static Specification<Task> statusIn(List<TaskStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    static Specification<Task> priorityIn(List<Priority> priorities) {
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    static Specification<Task> dueOnOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), from);
    }

    static Specification<Task> dueOnOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), to);
    }

    static Specification<Task> overdue(LocalDate today) {
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("dueDate"), today),
                cb.notEqual(root.get("status"), TaskStatus.DONE));
    }

    /** Case-insensitive "contains"; % and _ typed by the user are matched literally. */
    static Specification<Task> titleOrDescriptionContains(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
