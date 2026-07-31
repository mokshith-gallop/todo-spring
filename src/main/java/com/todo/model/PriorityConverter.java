package com.todo.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts Priority enum to/from its lowercase string representation.
 * PostgreSQL ENUM values are lowercase ('none','low','med','high')
 * while Java enum names are uppercase (NONE, LOW, MED, HIGH).
 * This converter bridges the two.
 */
@Converter(autoApply = false)
public class PriorityConverter implements AttributeConverter<Priority, String> {

    @Override
    public String convertToDatabaseColumn(Priority priority) {
        if (priority == null) {
            return null;
        }
        return priority.name().toLowerCase();
    }

    @Override
    public Priority convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Priority.valueOf(dbData.toUpperCase());
    }
}
