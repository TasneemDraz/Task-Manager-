package com.tasneem.taskmanager.task;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores Priority as its numeric level, so ORDER BY priority sorts LOW < MEDIUM < HIGH. */
@Converter(autoApply = true)
public class PriorityConverter implements AttributeConverter<Priority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Priority priority) {
        return priority == null ? null : priority.getLevel();
    }

    @Override
    public Priority convertToEntityAttribute(Integer level) {
        return level == null ? null : Priority.fromLevel(level);
    }
}
